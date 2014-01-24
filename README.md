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

The plugin relies on the `:relative-path` attribute of the `:parent`
vector in each module's project.clj. Via implicit Leiningen
middleware, it will merge any profiles named `:inherited` found among
its parents.

## License

Copyright © 2014 Jim Crossley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
