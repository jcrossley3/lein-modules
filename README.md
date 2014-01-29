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

Put `[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of
your `:user` profile.

Installed globally, the plugin will only affect those projects that
include a `:modules` map in their project.clj.

Still, if you'd rather not install it globally, put
`[lein-modules "0.1.0-SNAPSHOT"]` into the `:plugins` vector of every
associated module's project.clj.

## Configuration

This plugin relies on two keys in your project map: `:parent`,
specifically its `:relative-path` attribute, and `:modules`, a map
which may contain the following keys:

* `:inherited` - This is effectively a Leiningen profile. The implicit
  plugin middleware will merge the `:inherited` maps from all its
  ancestors, with the most immediate taking precedence, i.e. a parent
  will override a grandparent.
* `:versions` - Similar -- but **much** simpler -- to Maven's
  dependency management feature, versions for child module
  dependencies, plugins, and parent vectors will be expanded from this
  map. Symbols, e.g. `group-id/artifact-id`, from project dependency
  vectors are mapped to version strings that will replace those in the
  child project map. The map is recursively searched (values may be
  keys in the same map) to find a matching version string, useful when
  multiple dependencies share the same version. This allows you to
  maintain the versions of your child modules' shared dependencies in
  a single place. Just like with `:inherited`, the most immediate
  ancestors take precedence.
* `:dirs` - Normally, child modules are discovered by searching for
  project.clj files beneath the project's `:root` with a proper
  `:parent` reference, but this vector can override that behavior by
  specifying exactly which directories contain child modules. This
  vector is only required when your module hierarchy doesn't match
  your directory hierarchy, e.g. when a parent module is in a sibling
  directory. Regardless of this option, build order is always
  determined by child module interdependence.

## TODO

* Add an optional `:profiles` key in `:inherited` that indicates which
  parent profiles should be merged when the same profile is active in
  the child. Note `:profiles` and `:active-profiles` on project's
  metadata and expand any composite keys
* Versionization of `:plugins` is a bit of a chicken or egg problem:
  plugins are loaded before middleware is applied, so non-string
  versions will disappoint pomegranate.
* Consider replacing auto-versionization with a plugin subtask that
  spits out actual project.clj files for child modules, using the
  version strings from the `:versions` map. We'd want to preserve
  formatting and comments and probably leave `:profiles` alone. That
  could make straight regex replacement tricky.


## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
