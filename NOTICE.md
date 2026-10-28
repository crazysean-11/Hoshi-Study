# Attribution and modification notice

Hoshi Study is a modified derivative of Hoshi Reader Android, not an official upstream release.

- Android upstream: https://github.com/HuangAntimony/Hoshi-Reader-Android
- Original Hoshi Reader / iOS reference: https://github.com/Manhhao/Hoshi-Reader
- Public derivative: https://github.com/crazysean-11/Hoshi-Study
- Upstream Android source baseline: `8d10269466336a07d5ad38ffb2562e3569bda387`
- Modification/public-source preparation date: 2026-10-28
- License: GNU General Public License, version 3; see LICENSE. Existing copyright and third-party license notices remain applicable.

The fork adds AI explanations for selected passages, a local study notebook, combined review worksheets and SMTP delivery, e-ink defaults and Anki mining feedback/Basic template support. The public edition removes personal account defaults, credential provisioning, private build scripts, bundled dictionaries and device-specific paths, and uses a distinct application ID. A fresh source history protects private development metadata; it does not assert original authorship of the inherited reader.

Pinned reference dependencies are retained as public Git submodules:

- Hoshi Reader iOS: d24b2fa6e5fc559c7d4032b4db355eecdd669b29
- hoshidicts-kotlin-bridge: 35ab0f007f33acb45fa628d735b522c8f28e7ce5

See .gitmodules for source URLs and nested components for their individual licenses. Yomitan and AnkiDroid are independent upstream projects. No dictionary data or books are included in this public edition; obtain resources under their own applicable permissions. Optional native transcription components still reference upstream public releases and retain their own licensing notices.
