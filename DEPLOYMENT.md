# Building and installing Keep Score

The current direct Wear OS release is **v0.23.4**, Android version code **43**. To install or update the app on a watch, follow the [phone installation guide](INSTALLATION.md). The latest user-facing changes are in the [changelog](CHANGELOG.md).

## Development builds

Use Java 17 and Python 3 for the native project. The project records the Android dependencies and test libraries it uses. For the web application, use Node.js 22.13 or later and the package manager version in `package.json`. From the repository root, install the locked web dependencies with `npm run install:ci`. Build the web application with `npm run build`.

To build the watch from the public source, run:

```sh
python3 scripts/bootstrap_android.py
python3 scripts/build.py --sdk .local/android-sdk --junit .local/android-sdk/junit.jar --hamcrest .local/android-sdk/hamcrest-core.jar --unsigned
```

The aligned development APK is written to `build/0.23.4/aligned.apk`. For installation over an existing official app, use the release on the download page.

The native tests exercise Android views and scoring behavior. The web suite covers the phone interfaces, scoring interoperability and temporary sharing. See the [test guide](TESTING.md) for the commands and prerequisites appropriate to these suites.

## Source distribution

The public repository and downloadable source contain the application, tests, dependency declarations, guides and third-party notices. Run `python3 scripts/check_docs.py` to check guide links and release identifiers. The watch download page provides the signed release for installation. See [versioning](VERSIONING.md) for the direct and Google Play preparation channels.
