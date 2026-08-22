package io.github.devildare687.stealthmesh.util

/**
 * Extension function to convert a ByteArray to a hexadecimal string.
 */
fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02x".format(it) }
}
