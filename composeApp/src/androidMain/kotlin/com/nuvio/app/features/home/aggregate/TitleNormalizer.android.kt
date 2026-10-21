package com.nuvio.app.features.home.aggregate

import java.text.Normalizer

internal actual fun decomposeUnicode(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
