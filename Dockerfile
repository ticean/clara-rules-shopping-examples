FROM clojure:tools-deps-1.9.0.381-alpine

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

RUN apk add --update --no-cache \
      ca-certificates \
      git \
      graphviz \
      libc6-compat

# Use cached dependencies if deps.edn has not changed.
# We add all the aliases here to eagerly pre-fetch.
COPY deps.edn /usr/src/app/
RUN clojure -Abench:dev:outdated:pack:rebel:runner:test -Stree

COPY . /usr/src/app

# Pack an uberjar with juxt/pack.alpha
#RUN clojure -A:pack mach.pack.alpha.one-jar uberjar.jar
RUN clojure -A:pack mach.pack.alpha.capsule uberjar.jar \
      --application-id reaction.cart \
      --application-version "$(git rev-parse --verify HEAD)" \
      -m reaction.cart.main

CMD ["java", "-jar", "uberjar.jar"]
