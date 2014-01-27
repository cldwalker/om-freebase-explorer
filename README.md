## Description
This app combines two awesome things, [freebase's API](https://developers.google.com/freebase/) and
react, a la cljs thanks to [om](https://github.com/swannodette/om). This app aims to make freebase's
entities navigable from their API.

## Usage
This currently depends on an unreleased library:

```
$ git clone https://github.com/cldwalker/om-components
$ cd om-components
$ lein install
```

To run the app:

```sh
$ lein cljsbuild once dev
$ open resources/public/index.html
```

Currently you can only search and then click through one id.

## TODO
* Actually click through ids and discover what info freebase has on them.
* De butt-uglify it

## Credits
* Thanks to @cognitect for giving me time to knock some of this out on Fridays!
