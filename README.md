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

## Installation

Put `[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of
your `:user` profile.

Installed globally, the plugin will only affect those projects that
include a `:modules` map in their project.clj.

Still, if you'd rather not install it globally, put
`[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of every
associated module's project.clj.

## Usage

From a parent module, use the `modules` higher-order task to build its
"child" projects in the correct order, e.g.

    $ lein modules clean
    $ lein modules test
    $ lein modules jar
    $ lein modules analias

From a child module, just use `lein` as you normally would, relying on
the plugin's implicit middleware to 1) merge `:inherited` profiles,
and 2) update the child's project map from its ancestors' `:versions`
maps, both of which are described in the next section.

## Configuration

This plugin relies on two keys in your project map: `:parent`,
specifically its `:relative-path` attribute, and `:modules`, a map
which may contain the following keys:

* `:inherited` - This is effectively a Leiningen profile. The implicit
  plugin middleware will merge the `:inherited` maps from all its
  ancestors, with the most immediate taking precedence, i.e. a parent
  will override a grandparent. In addition, any profile maps found in
  parents will be merged if those profiles are active for the child.
  You may include a `:profiles` vector of keywords in `:inherited` to
  restrict which profiles are merged.
* `:versions` - A mapping of dependency symbols to version strings. As
  a simpler alternative to Maven's dependency management, versions for
  child module dependencies and parent vectors will be expanded from
  this map. Symbols, e.g. `group-id/artifact-id`, from project
  dependency vectors are mapped to version strings that will replace
  those in the child project map. The map is recursively searched
  (values may be keys in the same map) to find a matching version
  string, useful when multiple dependencies share the same version.
  This allows you to maintain the versions of your child modules'
  shared dependencies in a single place. Just like with `:inherited`,
  the most immediate ancestors take precedence.
* `:dirs` - A vector of relative paths. Normally, child modules are
  discovered by searching for project.clj files beneath the project's
  `:root` with a proper `:parent` reference, but this vector can
  override that behavior by specifying exactly which directories
  contain child modules. This vector is only required when your module
  hierarchy doesn't match your directory hierarchy, e.g. when a parent
  module is in a sibling directory. Regardless of this option, build
  order is always determined by child module interdependence.

## Example

Hopefully, a configuration example will clarify the above:

```clj
(defproject org.immutant/immutant-modules-parent "1.0.3-SNAPSHOT"
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :packaging "pom"

  :profiles {:provided
               {:dependencies [[org.clojure/clojure _]
                               [org.jboss.as/jboss-as-server _]]}
             :dev
               {:dependencies [[midje "1.6.0"]]}}

  :modules  {:inherited
               {:repositories [["project:odd upstream"
                                "http://repository-projectodd.forge.cloudbees.com/upstream"]]
                :source-paths       ["src/main/clojure"]
                :test-paths         ["src/test/clojure"]
                :java-source-paths  ["src/main/java"]
                :aliases ^:displace {"all" ["do" "clean," "test," "install"]}}
  
             :versions {org.clojure/clojure           "1.5.1"
                        leiningen-core/leiningen-core "2.3.4"

                        :immutant                     "1.0.3-SNAPSHOT"
                        :jbossas                      "7.2.x.slim.incremental.12"

                        org.immutant/immutant-modules-parent :immutant
                        org.immutant/immutant-core-module    :immutant
                        org.immutant/immutant-common-module  :immutant

                        org.jboss.as/jboss-as-server         :jbossas
                        org.jboss.as/jboss-as-jmx            :jbossas}

             :dirs ["messaging" "../web"]})
```

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
