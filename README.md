# lein-modules

On my continuum of hatred for all build tools,
[Leiningen](http://leiningen.org) and [Maven](http://maven.apache.org)
are at opposite ends. This plugin is the result of my desire to
transform the
[Immutant source tree](http://github.com/immutant/immutant) from a
Maven
[multi-module project](http://maven.apache.org/guides/mini/guide-multiple-modules.html)
to a Leiningen one.

I'm not done yet.

## Usage

Put `[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile. I doubt it will work with Leiningen 1.x.

It's probably less useful, or at least redundant since it would have
to be present in all the modules' project.clj, but for project-level
plugins:

Put `[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your project.clj.

## Configuration

All of the configuration for this plugin is contained in a map
associated with the key `:modules` in the project.clj of one or more
of your "parent" projects. The `:modules` map has three keys:

* `:inherited` - This is effectively a profile that will be merged
  into each project map of your "child" modules.
* `:versions` - **TODO** Similar to Maven's dependency management
  feature, but **much** simpler, child modules can use keywords as
  versions in their dependency vectors. Those keywords are associated
  to actual version strings in this map, allowing you to maintain the
  versions of your child modules' shared dependencies in a single
  place.
* `:dirs` - **TODO** Normally, child modules are discovered by
  searching for project.clj files, but this vector can override that
  behavior by specifying exactly which directories contain child
  modules. Whether this option is specified or not, build order is
  determined by the interdependence of all child modules.

The plugin relies on the `:relative-path` attribute of the `:parent`
vector in each module's project.clj. Via implicit Leiningen
middleware, this plugin will merge the `:inherited` maps of all parent
projects, with the most immediate ancestors taking precedence.

## TODO

* Add an optional :profiles key in :inherited that indicates which
  parent profiles should be merged when the same profile is active in
  the child. Note :profiles and :active-profiles on project's metadata
  and explode those keys where composite-profile? is true
* Lacking :dirs, search for child project.clj files and determine
  build order by module interdependence

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
