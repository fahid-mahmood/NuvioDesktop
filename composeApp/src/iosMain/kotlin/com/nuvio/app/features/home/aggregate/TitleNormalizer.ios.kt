package com.nuvio.app.features.home.aggregate

import platform.Foundation.NSString

internal actual fun decomposeUnicode(value: String): String =
    (value as NSString).decomposedStringWithCanonicalMapping()
