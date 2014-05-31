# lein-modules [![Build Status](https://travis-ci.org/jcrossley3/lein-modules.png?branch=master)](https://travis-ci.org/jcrossley3/lein-modules)

This plugin is the result of my desire to transform the
[Immutant source tree](http://github.com/immutant/immutant) from a
Maven
[multi-module project](http://maven.apache.org/guides/mini/guide-multiple-modules.html)
to a Leiningen one. It works well for a related suite of Leiningen
projects stored in a single SCM repository.

Features include the building of "child" projects in dependency order,
flexible project inheritance based on Leiningen profiles, a simple
dependency management mechanism, and automatic checkout dependencies.

Minimum supported versions:
* Leiningen: 2.3.4
* Clojure: 1.5.1

## Installation

Simply include `[lein-modules "0.3.4"]` in the `:plugins` vector of
your Leiningen project.

Installed globally, in your `:user` profile, the plugin's implicit
middleware will only affect those projects that include a `:modules`
map in their project.clj.

If you'd rather not install it globally, it needs to be in the
`:plugins` vector of every associated module's project.clj.

## Usage

From any "parent" project, use the `modules` higher-order task to
build its "child" projects in the correct order. When you first create
a project that has inter-dependent modules, you must install them to
your local repo prior to running any task that may attempt to resolve
them. You can do this easily from your root project:

    $ lein modules install

Once installed, you can run any task you like, e.g.:

    $ lein modules test
    $ lein modules deps :tree
    $ lein modules do clean, jar
    $ lein modules analias

By default, the task is not applied to the project in which you run
the `modules` task, only the child projects it finds. You can override
this behavior by adding `"."` to the `:dirs` vector.

In a child module, just use `lein` as you normally would, relying on
the plugin's implicit middleware to:

1. merge all ancestors' profiles
2. update the child's `:dependencies` from its ancestors' `:versions`
   maps.

See the Configuration section for more details on the supported
options.

### Checkout Dependencies

Run the following command to automatically create
[checkout dependencies](https://github.com/technomancy/leiningen/blob/stable/doc/TUTORIAL.md#checkout-dependencies)
for each related module:

    $ lein modules :checkouts

### Migrating from lein-sub

The `modules` task is similar to the `sub` task from the
[lein-sub](https://github.com/kumarshantanu/lein-sub) plugin. Consider
the following lein-sub configuration:

    :sub ["module/common" "module/web" "module/cli"]

The equivalent lein-modules configuration:

    :modules {:dirs ["module/common" "module/web" "module/cli"]
              :subprocess false}

Important differences:
* lein-sub builds the modules in the order you specify, but
  lein-modules always builds them in dependency order
* lein-sub always runs the tasks for each module in the same Leiningen
  process, while lein-modules spawns a new process for each *unless*
  `:subprocess` is set to false. See details below.
* Both support a command-line option to specify in which modules tasks
  are run, e.g.

```
    $ lein sub -s "foo:bar" jar
    $ lein modules :dirs "foo:bar" jar
```

If you don't require the dependency version management and project
inheritance features of lein-modules, lein-sub is an excellent
alternative.

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
  applied before the `:default` ones, but profile inheritance occurs
  whether you define an `:inherited` profile or not, because **all**
  profile maps from ancestors are automatically added to the child
  (excluding `:user` and any in the `:leiningen` namespace).
  Therefore, ancestor profiles such as `:dev`, `:provided`,
  `:production` or `:whatever` are [un]merged in the child as
  appropriate for the task at hand.

* `:versions` - A mapping of dependency symbols to version strings. As
  a simpler alternative to Maven's dependency management, versions for
  child module dependencies and parent vectors will be expanded from
  this map. It is recursively searched -- values may be keys in the
  same map -- for a version string using the following elements of the
  dependency vector, in order:

    1. the fully-qualified id field, `group-id/artifact-id`
    2. the version field
    3. the artifact id
    4. the group id

  The first non-nil value is returned, otherwise the dependency's
  version is returned. The project map's `:version` is automatically
  included in the map, so your interdependent modules may use this.
  This allows you to concisely maintain the versions of your child
  modules' shared dependencies in a single place. And like the
  `:inherited` profile, when multiple `:versions` maps are found among
  ancestors, the most immediate take precedence.

* `:dirs` - A vector of strings denoting the relative paths to the
  project's child modules. Normally, they're discovered automatically
  by searching for project.clj files beneath the project's `:root`
  with a related parent, but this vector can override that behavior by
  specifying exactly which directories contain child modules. This
  vector is only required when your module hierarchy doesn't match
  your directory hierarchy, e.g. when a parent module is in a sibling
  directory. Regardless of this option, build order is always
  determined by module interdependence.

* `:parent` - A string denoting the relative path to the parent
  project's directory. If unset, the value of the `:relative-path` of
  Leiningen's `:parent` vector will be used, and if that's unset, the
  default value is `".."`. You can explicitly set it to `nil` to
  signify that the project has no parent.

* `:subprocess` - The name of the executable invoked by the `modules`
  subtask for each child module in a separate process. Its default
  value is `"lein"`. You can optionally set it to false. This will
  speed up your build considerably since it runs every child module's
  task in the same process that invoked `lein modules`. This should be
  ok for most tasks, but can sometimes lead to surprises, e.g. hooks
  from one project can infect others, and the current working
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
(defproject org.immutant/immutant-suite "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.3.4"]]
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

                        :immutant                     :version
                        :jbossas                      "7.2.x.slim.incremental.12"

                        org.immutant/immutant-web     :immutant
                        org.immutant/immutant-core    :immutant
                        org.immutant/immutant-common  :immutant

                        org.jboss.as                  :jbossas}})
```

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
