# lein-modules

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

It has never been tested with any Leiningen version older than 2.3.4.

## Installation

Put `[lein-modules "0.1.0"]` into the `:plugins` vector of
your `:user` profile.

Installed globally, the plugin's implicit middleware will only affect
those projects that include a `:modules` map in their project.clj.

But if you'd rather not install it globally, put
`[lein-modules "0.1.0"]` into the `:plugins` vector of every
associated module's project.clj.

## Usage

From a parent module, use the `modules` higher-order task to build its
"child" projects in the correct order, e.g.

    $ lein modules clean
    $ lein modules test
    $ lein modules do clean, jar
    $ lein modules analias

From a child module, just use `lein` as you normally would, relying on
the plugin's implicit middleware to 1) merge inherited profiles, and
2) update the child's project map from its ancestors' `:versions`
maps, both of which are described in the next section.

## Configuration

For the `modules` task to discover child projects automatically, the
`:relative-path` should be set in the `:parent` vector of each child.

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
  this map. Fully-qualified symbols, e.g. `group-id/artifact-id`, from
  project dependency vectors are mapped to version strings that will
  replace those in the child project map. The map is searched
  recursively (values may be keys in the same map) to find a matching
  version string, useful when multiple dependencies share the same
  version. This allows you to maintain the versions of your child
  modules' shared dependencies in a single place. And like the
  `:inherited` profile, when multiple `:versions` maps are found among
  ancestors, the most immediate take precedence.
* `:dirs` - A vector of relative paths. Normally, child modules are
  discovered by searching for project.clj files beneath the project's
  `:root` with a proper `:parent` reference, but this vector can
  override that behavior by specifying exactly which directories
  contain child modules. This vector is only required when your module
  hierarchy doesn't match your directory hierarchy, e.g. when a parent
  module is in a sibling directory. Regardless of this option, build
  order is always determined by child module interdependence.

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
  :plugins [[lein-modules "0.1.0"]]
  :packaging "pom"

  :profiles {:provided
               {:dependencies [[org.clojure/clojure _]
                               [org.jboss.as/jboss-as-server _]
                               [org.jboss.as/jboss-as-web :jbossas]]}
             :dev
               {:dependencies [[midje _]
                               [ring/ring-devel "1.2.1"]]}
             :dist
               {:modules {:dirs ["../dist"]}}}

  :modules  {:inherited
               {:repositories [["project:odd upstream"
                                "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                :source-paths       ["src/main/clojure"]
                :test-paths         ["src/test/clojure"]
                :java-source-paths  ["src/main/java"]
                :aliases ^:displace {"all" ["do" "clean," "test," "install"]}}
  
             :versions {org.clojure/clojure           "1.5.1"
                        leiningen-core/leiningen-core "2.3.4"
                        midje/midje                   "1.6.0"

                        :immutant                     "1.0.3-SNAPSHOT"
                        :jbossas                      "7.2.x.slim.incremental.12"

                        org.immutant/immutant-parent  :immutant
                        org.immutant/immutant-core    :immutant
                        org.immutant/immutant-common  :immutant

                        org.jboss.as/jboss-as-server  :jbossas
                        org.jboss.as/jboss-as-jmx     :jbossas}})
```

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
