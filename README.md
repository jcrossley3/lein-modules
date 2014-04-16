# lein-modules [![Build Status](https://travis-ci.org/jcrossley3/lein-modules.png?branch=master)](https://travis-ci.org/jcrossley3/lein-modules)

On my build tool continuum of hate, [Leiningen](http://leiningen.org)
and [Maven](http://maven.apache.org) are at opposite ends. This plugin
is the result of my desire to transform the
[Immutant source tree](http://github.com/immutant/immutant) from a
Maven
[multi-module project](http://maven.apache.org/guides/mini/guide-multiple-modules.html)
to a Leiningen one.

Features include the building of child projects in dependency order,
flexible project inheritance based on Leiningen profiles, and a simple
dependency management mechanism.

Minimum supported Leiningen version: 2.3.4

Minimum supported Clojure version: 1.5.1

## Installation

Put `[lein-modules "0.2.4"]` into the `:plugins` vector of
your `:user` profile.

Installed globally, the plugin's implicit middleware will only affect
those projects that include a `:modules` map in their project.clj.

But if you'd rather not install it globally, put
`[lein-modules "0.2.4"]` into the `:plugins` vector of every
associated module's project.clj.

## Usage

From a parent module, use the `modules` higher-order task to build its
"child" projects in the correct order. When you first create a project
that has inter-dependent modules, you must install them to your local
repo prior to running any task that may attempt to resolve them. You
can do this easily from the root:

    $ lein modules install

Once installed, you can run any task you like from the root:

    $ lein modules test
    $ lein modules deps :tree
    $ lein modules do clean, jar
    $ lein modules analias

From a child module, just use `lein` as you normally would, relying on
the plugin's implicit middleware to 1) merge inherited profiles, and
2) update the child's project map from its ancestors' `:versions`
maps, both of which are described in the next section.

## Configuration

The `modules` task will attempt to discover child projects
automatically, making the default assumption that each child project
resides in an immediate subdirectory of its parent.

Optionally, a `:modules` map may be added to your project, containing
any of the following keys:

* `:inherited` - This is just a Leiningen profile. You could
  alternatively put it in `:profiles` to emphasize that point. The
  implicit plugin middleware will create composite profiles for all
  the profile maps found among a project's ancestors, with the most
  immediate taking precedence, i.e. a parent profile will be applied
  after a grandparent. If found, the `:inherited` profiles will be
  applied before the `:default` profiles, but you can override this
  behavior by not creating any `:inherited` profiles and setting
  `:default` to be a composite of whatever profiles make sense for
  your project, including the default ones. This bears repeating:
  profile inheritance occurs whether you define an `:inherited`
  profile or not, because **all** profile maps from ancestors, e.g.
  `:dev`, `:provided`, `:production` or `:whatever`, are automatically
  added to the child. Therefore, they are [un]merged as appropriate
  for the task at hand.
* `:versions` - A mapping of dependency symbols to version strings. As
  a simpler alternative to Maven's dependency management, versions for
  child module dependencies and parent vectors will be expanded from
  this map. It is recursively searched -- values may be keys in the
  same map -- for a version string using the following keys, in order:
  the fully-qualified id field, `group-id/artifact-id`, of the
  dependency vector, then its version field, then just the artifact
  id, and finally just the group id. The first non-nil value is
  returned, otherwise the dependency's version is returned. This
  allows you to concisely maintain the versions of your child modules'
  shared dependencies in a single place. And like the `:inherited`
  profile, when multiple `:versions` maps are found among ancestors,
  the most immediate take precedence.
* `:dirs` - A vector of strings denoting the relative paths to the
  project's child modules. Normally, they're discovered automatically
  by searching for project.clj files beneath the project's `:root`
  with a related parent, but this vector can override that behavior by
  specifying exactly which directories contain child modules. This
  vector is only required when your module hierarchy doesn't match
  your directory hierarchy, e.g. when a parent module is in a sibling
  directory. Regardless of this option, build order is always
  determined by child module interdependence.
* `:parent` - A string denoting the relative path to the parent
  project's directory. If unset, the value of the `:relative-path` of
  Leiningen's `:parent` vector will be used, and if that's unset, the
  default value is `".."`. You can explicitly set it to `nil` to
  signify that the project has no parent.
* `:subprocess` - The name of the executable invoked by the `modules`
  subtask for each child module in a separate process. Its default
  value is `"lein"`. You can optionally set it to false. This will
  speed up your build considerably since it runs every child module's
  task in the same process that invoked `lein modules ...` This should
  be ok for most tasks, but can sometimes lead to surprises, e.g.
  hooks from one project can infect others, and the current working
  directory won't match the `:root` of the child project. Still, for
  common tasks like `clean` it can be convenient to configure a
  `:fast` profile that sets `:subprocess` to false for projects with
  lots of child modules.

## Example

Hopefully, an example will clarify the above.

Note the underscores in the dependency vectors, which serve as a
placeholder for the string returned from the `:versions` map. Whatever
you set the version to in your dependency vector will be overwritten
if a version is found in `:versions`. Otherwise, whatever is there
will remain there. And if a mapping for the symbol can't be found, the
version itself will be tried as a key.

```clj
(defproject org.immutant/immutant-parent "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.2.4"]]
  :packaging "pom"

  :profiles {:provided
               {:dependencies [[org.clojure/clojure "_"]
                               [org.jboss.as/jboss-as-server "_"]
                               [org.jboss.as/jboss-as-web :jbossas]]}
             :dev
               {:dependencies [[midje "_"]
                               [ring/ring-devel "1.2.1"]]}
             :dist
               {:modules {:dirs ["../dist"]}}

             :fast
               {:modules {:subprocess false}}}

  :modules  {:inherited
               {:repositories [["project:odd upstream"
                                "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                :source-paths       ["src/main/clojure"]
                :test-paths         ["src/test/clojure"]
                :java-source-paths  ["src/main/java"]
                :aliases            {"all" ^:displace ["do" "clean," "test," "install"]
                                     "-f" ["with-profile" "+fast"]}}

             :versions {org.clojure/clojure           "1.5.1"
                        leiningen-core                "2.3.4"
                        midje                         "1.6.0"

                        :immutant                     "1.0.3-SNAPSHOT"
                        :jbossas                      "7.2.x.slim.incremental.12"

                        org.immutant/immutant-parent  :immutant
                        org.immutant/immutant-core    :immutant
                        org.immutant/immutant-common  :immutant

                        org.jboss.as                  :jbossas}})
```

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
