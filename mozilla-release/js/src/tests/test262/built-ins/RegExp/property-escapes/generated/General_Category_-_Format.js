// Copyright 2017 Mathias Bynens. All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.

/*---
author: Mathias Bynens
description: >
  Unicode property escapes for `General_Category=Format`
info: |
  Generated by https://github.com/mathiasbynens/unicode-property-escapes-tests
  Unicode v9.0.0
  Emoji v5.0 (UTR51)
esid: sec-static-semantics-unicodematchproperty-p
features: [regexp-unicode-property-escapes]
includes: [regExpUtils.js]
---*/

const matchSymbols = buildString({
  loneCodePoints: [
    0x0000AD,
    0x00061C,
    0x0006DD,
    0x00070F,
    0x0008E2,
    0x00180E,
    0x00FEFF,
    0x0110BD,
    0x0E0001
  ],
  ranges: [
    [0x000600, 0x000605],
    [0x00200B, 0x00200F],
    [0x00202A, 0x00202E],
    [0x002060, 0x002064],
    [0x002066, 0x00206F],
    [0x00FFF9, 0x00FFFB],
    [0x01BCA0, 0x01BCA3],
    [0x01D173, 0x01D17A],
    [0x0E0020, 0x0E007F]
  ]
});
testPropertyEscapes(
  /^\p{General_Category=Format}+$/u,
  matchSymbols,
  "\\p{General_Category=Format}"
);
testPropertyEscapes(
  /^\p{General_Category=Cf}+$/u,
  matchSymbols,
  "\\p{General_Category=Cf}"
);
testPropertyEscapes(
  /^\p{gc=Format}+$/u,
  matchSymbols,
  "\\p{gc=Format}"
);
testPropertyEscapes(
  /^\p{gc=Cf}+$/u,
  matchSymbols,
  "\\p{gc=Cf}"
);
testPropertyEscapes(
  /^\p{Format}+$/u,
  matchSymbols,
  "\\p{Format}"
);
testPropertyEscapes(
  /^\p{Cf}+$/u,
  matchSymbols,
  "\\p{Cf}"
);

const nonMatchSymbols = buildString({
  loneCodePoints: [
    0x002065
  ],
  ranges: [
    [0x00DC00, 0x00DFFF],
    [0x000000, 0x0000AC],
    [0x0000AE, 0x0005FF],
    [0x000606, 0x00061B],
    [0x00061D, 0x0006DC],
    [0x0006DE, 0x00070E],
    [0x000710, 0x0008E1],
    [0x0008E3, 0x00180D],
    [0x00180F, 0x00200A],
    [0x002010, 0x002029],
    [0x00202F, 0x00205F],
    [0x002070, 0x00DBFF],
    [0x00E000, 0x00FEFE],
    [0x00FF00, 0x00FFF8],
    [0x00FFFC, 0x0110BC],
    [0x0110BE, 0x01BC9F],
    [0x01BCA4, 0x01D172],
    [0x01D17B, 0x0E0000],
    [0x0E0002, 0x0E001F],
    [0x0E0080, 0x10FFFF]
  ]
});
testPropertyEscapes(
  /^\P{General_Category=Format}+$/u,
  nonMatchSymbols,
  "\\P{General_Category=Format}"
);
testPropertyEscapes(
  /^\P{General_Category=Cf}+$/u,
  nonMatchSymbols,
  "\\P{General_Category=Cf}"
);
testPropertyEscapes(
  /^\P{gc=Format}+$/u,
  nonMatchSymbols,
  "\\P{gc=Format}"
);
testPropertyEscapes(
  /^\P{gc=Cf}+$/u,
  nonMatchSymbols,
  "\\P{gc=Cf}"
);
testPropertyEscapes(
  /^\P{Format}+$/u,
  nonMatchSymbols,
  "\\P{Format}"
);
testPropertyEscapes(
  /^\P{Cf}+$/u,
  nonMatchSymbols,
  "\\P{Cf}"
);

reportCompare(0, 0);