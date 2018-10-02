# clara shopping examples

Extended shopping logic examples from Clara Rules.

Adds modifications to the original examples to provide more detail and make it
real-world.

The original examples can be found at https://github.com/cerner/clara-examples

## Usage

The project contains interactive Clara Rules examples that are intended to run
from the REPL.

Example, from REPL:

```clojure
(require '[ticean.clara.shopping :as s])
(s/run-examples)
```

### The REPL Environment

To begin developing, start with a REPL.

```sh
docker-compose run --rm --service-ports web bin/dev
```

Then load the development namespace.

```clojure
user=> (dev)
:loaded
```

### Reloading Code in the REPL

When you make changes to your source files, use `refresh` to reload any
modified files.

```clojure
dev=> (refresh)
:loaded
```


## License

Copyright 2016 Cerner Innovation, Inc.  
Copyright 2018 Ticean Bennett

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
