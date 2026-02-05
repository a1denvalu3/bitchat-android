package com.bitchat.android

import org.junit.Test
import org.junit.Assert.assertEquals
import kotlin.random.Random

class RandomInverseTest {

    // Constants for SplitMix64 (ThreadLocalRandom)
    private val GAMMA: Long = -7046029254386353131L // 0x9e3779b97f4a7c15L
    private val MIX_CONST_1: Long = -49064778989728563L // 0xff51afd7ed558ccdL
    private val MIX_CONST_2: Long = -4265267296055464877L // 0xc4ceb9fe1a85ec53L
    
    // Modular Multiplicative Inverses modulo 2^64
    // 0xff51afd7ed558ccd * INV_K1 = 1 mod 2^64
    private val INV_K1: Long = 5725274745694666757L // 0x4f74430c22a54005L

    // 0xc4ceb9fe1a85ec53 * INV_K2 = 1 mod 2^64
    private val INV_K2: Long = -7154897129451604005L // 0x9cb4b2f8129337dbL (unsigned)

    // Forward mixing function (from ThreadLocalRandom)
    private fun mix64(z: Long, debug: Boolean = false): Long {
        var x = z
        val s1 = x xor (x ushr 33)
        if (debug) println("mix64: step1 (xor) = $s1")
        
        x = s1 * MIX_CONST_1
        if (debug) println("mix64: step2 (mul1) = $x")
        
        val s3 = x xor (x ushr 33)
        if (debug) println("mix64: step3 (xor) = $s3")
        
        x = s3 * MIX_CONST_2
        if (debug) println("mix64: step4 (mul2) = $x")
        
        val res = x xor (x ushr 33)
        if (debug) println("mix64: result = $res")
        return res
    }

    // Inverse function to recover seed from output
    private fun recoverSeed(output: Long, debug: Boolean = false): Long {
        if (debug) println("recover: input = $output")
        
        // 1. Invert final XOR-shift (k=33 >= 32, so self-inverse)
        val z2 = output xor (output ushr 33)
        if (debug) println("recover: step1 (xor) = $z2")

        // 2. Invert second multiplication
        val z1_temp = z2 * INV_K2
        if (debug) println("recover: step2 (mul_inv2) = $z1_temp")

        // 3. Invert middle XOR-shift (k=33 >= 32, so self-inverse)
        val z1 = z1_temp xor (z1_temp ushr 33)
        if (debug) println("recover: step3 (xor) = $z1")

        // 4. Invert first multiplication
        val z0_temp = z1 * INV_K1
        if (debug) println("recover: step4 (mul_inv1) = $z0_temp")

        // 5. Invert initial XOR-shift (k=33 >= 32, so self-inverse)
        val currentSeed = z0_temp xor (z0_temp ushr 33)
        if (debug) println("recover: result = $currentSeed")

        return currentSeed
    }

    @Test
    fun testConstants() {
        println("Testing Constants Inversion:")
        val p1 = MIX_CONST_1 * INV_K1
        println("MIX_CONST_1 * INV_K1 = $p1 (Hex: ${java.lang.Long.toHexString(p1)})")
        assertEquals("MIX_CONST_1 * INV_K1 != 1", 1L, p1)

        val p2 = MIX_CONST_2 * INV_K2
        println("MIX_CONST_2 * INV_K2 = $p2 (Hex: ${java.lang.Long.toHexString(p2)})")
        assertEquals("MIX_CONST_2 * INV_K2 != 1", 1L, p2)
    }

    @Test
    fun testSelfConsistency() {
        // Use a fixed seed to verify mathematical inverse properties
        val seed = -123456789L // Use a negative number to test sign handling
        val mixed = mix64(seed, debug = true)
        val recovered = recoverSeed(mixed, debug = true)
        
        println("Self-Consistency Check:")
        println("Seed:      $seed")
        println("Mixed:     $mixed")
        println("Recovered: $recovered")
        
        assertEquals("Self-consistency failed: recoverSeed(mix64(x)) != x", seed, recovered)
    }

    @Test
    fun testRandomInversion() {
        println("Testing Random Inversion...")
        
        // 1. Get a random number from ThreadLocalRandom
        val tlr = java.util.concurrent.ThreadLocalRandom.current()
        val r1 = tlr.nextLong()
        println("Output 1: $r1")
        
        // 2. Recover the seed
        val seed1 = recoverSeed(r1)
        println("Recovered Seed 1: $seed1")
        
        // 3. Predict next
        // On Android 30 (OpenJDK 8), GAMMA is fixed: 0x9e3779b97f4a7c15L
        val GAMMA_ANDROID = -7046029254386353131L // 0x9e3779b97f4a7c15L
        
        // On OpenJDK 14+ (including 21), ThreadLocalRandom adds (threadId << 1) to GAMMA
        // to ensure different streams for different threads.
        val threadId = Thread.currentThread().id
        val GAMMA_JDK_MODERN = GAMMA_ANDROID + (threadId shl 1)
        
        val seed2_android = seed1 + GAMMA_ANDROID
        val r2_predicted_android = mix64(seed2_android)
        
        val seed2_jdk_modern = seed1 + GAMMA_JDK_MODERN
        val r2_predicted_jdk_modern = mix64(seed2_jdk_modern)
        
        val r2_actual = tlr.nextLong()
        println("Actual Output 2:      $r2_actual")
        println("Predicted (Android):  $r2_predicted_android")
        println("Predicted (JDK Mod):  $r2_predicted_jdk_modern (Thread ID: $threadId)")
        
        val matchedAndroid = r2_actual == r2_predicted_android
        val matchedJDKModern = r2_actual == r2_predicted_jdk_modern
        
        if (matchedAndroid) {
            println("SUCCESS: Matched Android/OpenJDK8 behavior (Fixed GAMMA)")
        } else if (matchedJDKModern) {
            println("SUCCESS: Matched Host OpenJDK behavior (GAMMA + 2*ThreadID)")
        } else {
            // Fail if neither matches
             assertEquals("Prediction failed for both Android and Host GAMMA variants", r2_predicted_android, r2_actual)
        }
    }
}
