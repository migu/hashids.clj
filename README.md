# hashids.clj

A Clojure library to generate short unique ids from integers

## Usage

### Setting up

You can pass a unique salt value so your hashes differ from everyone else's.
I use **this is my salt** as an example.

```clojure
(require '[hashids.core :as h])
(def hashids-opts {:salt "this is my salt"}) ;; These options are used for most examples below
```

### Encoding

```clojure
;; Encode a single number
(h/encode hashids-opts 12345) ;; => "NkK9"

;; Encode a set of numbers
(h/encode hashids-opts 683, 94108, 123, 5) ;; => "aBMswoO2UB3Sj"

;; or a collection
(h/encode hashids-opts [683, 94108, 123, 5]) ;; => "aBMswoO2UB3Sj"

;; A minimum length can be set for the encoded string
(h/encode {:salt "this is my salt" :min-length 4} 12345) ;; => "NkK9"
(h/encode {:salt "this is my salt" :min-length 5} 12345) ;; => "0NkK9"
(h/encode {:salt "this is my salt" :min-length 6} 12345) ;; => "0NkK9A"
(h/encode {:salt "this is my salt" :min-length 7} 12345) ;; => "10NkK9A"

;; Use a custom alphabet
(h/encode {:alphabet "0123456789uvwxyz"} 12345) ;; => "v95w8x"
```

### Decoding

Notice during decoding, same salt value is used:

```clojure
(h/decode hashids-opts "NkK9") ;; => (12345)
(h/decode hashids-opts "aBMswoO2UB3Sj") ;; => (683, 94108, 123, 5)

;; Decoding with a wrong salt will return an empty collection
(h/decode {:salt "wrong salt"} "aBMswoO2UB3Sj") ;; => ()

;; Decoding a string which is more than the given alphabet will return
;; an empty collection also
(h/decode {:alphabet "0123456789uvwxyz"} "PPPP") ;; => ()
```

## Randomness

The primary purpose of hashids is to obfuscate ids. It's not meant or tested to be used for security purposes or compression.
Having said that, this algorithm does try to make these hashes unguessable and unpredictable:

### Repeating numbers

You don't see any repeating patterns for identical numbers:

```clojure
(h/encode hashids-opts 5 5 5 5) ;; => "1Wc8cwcE"
```

Same with incremented numbers:

```clojure
(h/encode hashids-opts 1 2 3 4 5 6 7 8 9 10) ;; => "kRHnurhptKcjIDTWC3sx"

### Incrementing number ids:

```clojure
(h/encode hashids-opts 1) ;; => "NV"
(h/encode hashids-opts 2) ;; => "6m"
(h/encode hashids-opts 3) ;; => "yD"
(h/encode hashids-opts 4) ;; => "2l"
(h/encode hashids-opts 5) ;; => "rD"
```


## License

Copyright © 2015 Jason Strutz

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
