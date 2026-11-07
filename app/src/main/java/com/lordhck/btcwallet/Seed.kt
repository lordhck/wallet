package com.lordhck.btcwallet

import android.content.Context
import org.bitcoindevkit.Bip39Exception
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.WordCount

// BIP39 seed phrases, done by BDK
object Seed {
    private var wordlist: List<String>? = null

    // The 2048 BIP39 words
    fun wordlist(context: Context): List<String> = wordlist
        ?: context.assets.open("bip39.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            .also { wordlist = it }

    fun generate(): List<String> = Mnemonic(WordCount.WORDS12).toString().split(" ")

    fun split(text: String): List<String> =
        text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    // The words if they are a valid 12 or 24 word phrase, otherwise null
    fun parse(text: String): List<String>? {
        val words = split(text)
        if (words.size != 12 && words.size != 24) return null
        return try {
            Mnemonic.fromString(words.joinToString(" "))
            words
        } catch (e: Bip39Exception) {
            null
        }
    }
}
