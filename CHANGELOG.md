# Changelog

## [1.3.1](https://github.com/lattemacchiato/soundboard/compare/v1.3.0...v1.3.1) (2026-11-09)


### Bug Fixes

* **backup:** support folder sounds in full backup and restore ([3db4ae4](https://github.com/lattemacchiato/soundboard/commit/3db4ae4d2fc82aac6d1023b1dce6fdee72f52ab0))

## [1.3.0](https://github.com/lattemacchiato/soundboard/compare/v1.2.0...v1.3.0) (2026-11-08)


### Features

* add per-sound playback segments ([af74654](https://github.com/lattemacchiato/soundboard/commit/af74654868082c775ce3dae5ac6a442b2cc8323d))
* explain notification permission and expose settings ([627b37a](https://github.com/lattemacchiato/soundboard/commit/627b37a520c5cbdacf623b183e902e1082876913))
* explain notification permission and expose settings ([c0ad858](https://github.com/lattemacchiato/soundboard/commit/c0ad858715ae3c5cea173edfab6f1e1c429f85fc))
* show per-sound tap behavior indicator ([ae1f481](https://github.com/lattemacchiato/soundboard/commit/ae1f481695e50c75866afa633f8c0282904b8005))
* show per-sound tap behavior indicators ([4a438db](https://github.com/lattemacchiato/soundboard/commit/4a438db0d9c7356c5f3b01b1fb9bd1b1912f7816))


### Bug Fixes

* correct screenshot file permissions ([ef4d4e8](https://github.com/lattemacchiato/soundboard/commit/ef4d4e8a449fcce0d74a0f00a9edef5c31818d2c))
* replace deprecated Material 3 menu anchor API ([9408105](https://github.com/lattemacchiato/soundboard/commit/9408105cea218fbe475a9e8ce6cac8293b350bfb))
* replace deprecated Material 3 menu anchor API ([4700bce](https://github.com/lattemacchiato/soundboard/commit/4700bce380d1144b21078325b1222ca87bcd149e))

## [1.2.0](https://github.com/lattemacchiato/soundboard/compare/v1.1.1...v1.2.0) (2026-11-07)


### Features

* add folder picker and persistent folder tiles ([3f183ea](https://github.com/lattemacchiato/soundboard/commit/3f183ea99c3f0ce894fe5da415eab7a6672201b8))
* add folder playback with alphabetical and shuffle modes ([19e4aec](https://github.com/lattemacchiato/soundboard/commit/19e4aecfd9152828d3ddd24379385a1573b55d14))
* extend sound model for folder playback ([a8586b7](https://github.com/lattemacchiato/soundboard/commit/a8586b7a524a44ed9549715a8b5b415fe29fefd8))
* implement basic folder playlist playback ([652b615](https://github.com/lattemacchiato/soundboard/commit/652b6154146f1fe7adc49c9762859ddb513fc527))
* simplify playback controls in performance lock mode ([d6a575f](https://github.com/lattemacchiato/soundboard/commit/d6a575f8e9250431392546b595bc94ff6d30145e))

## [1.1.1](https://github.com/lattemacchiato/soundboard/compare/v1.1.0...v1.1.1) (2026-10-31)


### Bug Fixes

* **ci:** use separate release pull requests ([dc2c96b](https://github.com/lattemacchiato/soundboard/commit/dc2c96b211b674746ca0c46e4ad7fe5199a4f824))
* **ci:** use separate release pull requests ([4b22bc2](https://github.com/lattemacchiato/soundboard/commit/4b22bc2c757f3423514f0bca361fb0fb4bcab54e))

## [1.1.0](https://github.com/lattemacchiato/soundboard/compare/v1.0.0...v1.1.0) (2026-10-31)


### Features

* add configurable sound button size with per-board overrides ([69f53a6](https://github.com/lattemacchiato/soundboard/commit/69f53a6388ec803ca12b21955ffb2b44493a0835))
* add global default loop setting for new sounds ([fe2ac15](https://github.com/lattemacchiato/soundboard/commit/fe2ac15b6613682d95e6cb5e31663e620c63370c))
* improve board navigation and selection UX ([43660d0](https://github.com/lattemacchiato/soundboard/commit/43660d0fd2fceb1e90d58f6652df8a7bee6119f2))
* improve theme settings layout ([aaf6100](https://github.com/lattemacchiato/soundboard/commit/aaf61005b500a522e1eaa9ff001842e64e8059f0))
* move backup and restore to navigation drawer ([7b2d34b](https://github.com/lattemacchiato/soundboard/commit/7b2d34b82ce3804537a54c506d0cf5b551852c92))
* move sound edit and delete actions to sheet header ([31fd9c2](https://github.com/lattemacchiato/soundboard/commit/31fd9c232e6dde1d675ec5c9ceb96ee827322099))
* show loop indicator on sound rows ([2f586df](https://github.com/lattemacchiato/soundboard/commit/2f586df47e112e131ae2c84d46ea444fb09563b7))
* streamline board management UI and deletion navigation ([4490589](https://github.com/lattemacchiato/soundboard/commit/449058933f8d852fbbc07c4e536748d65c32032c))
* unify about and licenses back navigation ([b006137](https://github.com/lattemacchiato/soundboard/commit/b0061370066ceb4e9d75a752f4fe6e565e9527b1))


### Bug Fixes

* accept restored audio file URIs in configuration backups ([63264b9](https://github.com/lattemacchiato/soundboard/commit/63264b986ed51a23dc6dd4d510211b0802a20784))

## 1.0.0

First Soundboard NG release, based on Soundboard by 508.dev.

- Multiple soundboards and performance lock mode
- Play/pause, restart, looping, independent volume and configurable fades
- Individual tap behaviour and optional simultaneous playback
- JSON and portable ZIP backup and restore
- System, light and dark themes
- Improved navigation and safe exit confirmation
- Rebranded app and launcher icon
