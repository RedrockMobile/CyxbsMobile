/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.cyxbs.components.utils.utils.secret

/**
 * Stripped-down version of the SHA1PRNG provided by the Crypto provider.
 *
 *
 * The Crypto provider that offers this functionality was deprecated on Android.
 *
 *
 * Use this class only to retrieve encrypted data that couldn't be retrieved otherwise.
 */
class InsecureSHA1PRNGKeyDerivator private constructor() {
  // Structure of "seed" array:
  // -  0-79 - words for computing hash
  // - 80    - unused
  // - 81    - # of seed bytes in current seed frame
  // - 82-86 - 5 words, current seed hash
  @Transient
  private val seed: IntArray

  // total length of seed bytes, including all processed
  @Transient
  private var seedLength: Long

  // Structure of "copies" array
  // -  0-4  - 5 words, copy of current seed hash
  // -  5-20 - extra 16 words frame;
  //           is used if final padding exceeds 512-bit length
  // - 21-36 - 16 word frame to store a copy of remaining bytes
  @Transient
  private val copies: IntArray

  // ready "next" bytes; needed because words are returned
  @Transient
  private val nextBytes: ByteArray

  // index of used bytes in "nextBytes" array
  @Transient
  private var nextBIndex: Int

  // variable required according to "SECURE HASH STANDARD"
  @Transient
  private var counter: Long

  // contains int value corresponding to engine's current state
  @Transient
  private var state: Int

  // The "seed" array is used to compute both "current seed hash" and "next bytes".
  //
  // As the "SHA1" algorithm computes a hash of entire seed by splitting it into
  // a number of the 512-bit length frames (512 bits = 64 bytes = 16 words),
  // "current seed hash" is a hash (5 words, 20 bytes) for all previous full frames;
  // remaining bytes are stored in the 0-15 word frame of the "seed" array.
  //
  // As for calculating "next bytes",
  // both remaining bytes and "current seed hash" are used,
  // to preserve the latter for following "setSeed(..)" commands,
  // the following technique is used:
  // - upon getting "nextBytes(byte[])" invoked, single or first in row,
  //   which requires computing new hash, that is,
  //   there is no more bytes remaining from previous "next bytes" computation,
  //   remaining bytes are copied into the 21-36 word frame of the "copies" array;
  // - upon getting "setSeed(byte[])" invoked, single or first in row,
  //   remaining bytes are copied back.
  init {
    seed = IntArray(HASH_OFFSET + EXTRAFRAME_OFFSET)
    seed[HASH_OFFSET] = H0
    seed[HASH_OFFSET + 1] = H1
    seed[HASH_OFFSET + 2] = H2
    seed[HASH_OFFSET + 3] = H3
    seed[HASH_OFFSET + 4] = H4
    seedLength = 0
    copies = IntArray(2 * FRAME_LENGTH + EXTRAFRAME_OFFSET)
    nextBytes = ByteArray(DIGEST_LENGTH)
    nextBIndex = HASHBYTES_TO_USE
    counter = COUNTER_BASE.toLong()
    state = UNDEFINED
  }

  /*
     * The method invokes the SHA1Impl's "updateHash(..)" method
     * to getUpdateInfo current seed frame and
     * to compute new intermediate hash value if the frame is full.
     *
     * After that it computes a length of whole seed.
     */
  private fun updateSeed(bytes: ByteArray) {
    // on call:   "seed" contains current bytes and current hash;
    // on return: "seed" contains new current bytes and possibly new current hash
    //            if after adding, seed bytes overfill its buffer
    updateHash(seed, bytes, 0, bytes.size - 1)
    seedLength += bytes.size.toLong()
  }

  /**
   * Changes current seed by supplementing a seed argument to the current seed,
   * if this already set;
   * the argument is used as first seed otherwise. <BR></BR>
   *
   *
   * The method overrides "engineSetSeed(byte[])" in class SecureRandomSpi.
   *
   * @param seed - byte array
   * @throws NullPointerException - if null is passed to the "seed" argument
   */
  private fun setSeed(seed: ByteArray) {
    if (state == NEXT_BYTES) { // first setSeed after NextBytes; restoring hash
      System.arraycopy(
        copies, HASHCOPY_OFFSET,
        this.seed, HASH_OFFSET,
        EXTRAFRAME_OFFSET
      )
    }
    state = SET_SEED
    if (seed.size != 0) {
      updateSeed(seed)
    }
  }

  /**
   * Writes random bytes into an array supplied.
   * Bits in a byte are from left to right. <BR></BR>
   *
   *
   * To generate random bytes, the "expansion of source bits" method is used,
   * that is,
   * the current seed with a 64-bit counter appended is used to compute new bits.
   * The counter is incremented by 1 for each 20-byte output. <BR></BR>
   *
   *
   * The method overrides engineNextBytes in class SecureRandomSpi.
   *
   * @param bytes - byte array to be filled in with bytes
   * @throws NullPointerException - if null is passed to the "bytes" argument
   */
  @Synchronized
  protected fun nextBytes(bytes: ByteArray) {
    var i: Int
    var n: Int
    val bits: Long // number of bits required by Secure Hash Standard
    val lastWord: Int // index of last word in frame containing bytes
    // This is a bug since words are 4 bytes. Android used to keep it this way for backward
    // compatibility.
    val extrabytes = 7 // # of bytes to add in order to computer # of 8 byte words
    // This is a bug since extraBytes == 7 instead of 3. Android used to keep it this way for
    // backward compatibility.
    lastWord = if (seed[BYTES_OFFSET] == 0)
      0
    else
      (seed[BYTES_OFFSET] + extrabytes) shr 3 - 1
    check(state != UNDEFINED) { "No seed supplied!" }
    if (state == SET_SEED) {
      System.arraycopy(
        seed, HASH_OFFSET, copies, HASHCOPY_OFFSET,
        EXTRAFRAME_OFFSET
      )
      // possible cases for 64-byte frame:
      //
      // seed bytes < 48      - remaining bytes are enough for all, 8 counter bytes,
      //                        0x80, and 8 seedLength bytes; no extra frame required
      // 48 < seed bytes < 56 - remaining 9 bytes are for 0x80 and 8 counter bytes
      //                        extra frame contains only seedLength value at the end
      // seed bytes > 55      - extra frame contains both counter's bytes
      //                        at the beginning and seedLength value at the end;
      //                        note, that beginning extra bytes are not more than 8,
      //                        that is, only 2 extra words may be used
      // no need to set to "0" 3 words after "lastWord" and
      // more than two words behind frame
      i = lastWord + 3
      while (i < FRAME_LENGTH + 2) {
        seed[i] = 0
        i++
      }
      bits = (seedLength shl 3) + 64 // transforming # of bytes into # of bits
      // putting # of bits into two last words (14,15) of 16 word frame in
      // seed or copies array depending on total length after padding
      if (seed[BYTES_OFFSET] < MAX_BYTES) {
        seed[14] = (bits ushr 32).toInt()
        seed[15] = (bits and 0xFFFFFFFFL).toInt()
      } else {
        copies[EXTRAFRAME_OFFSET + 14] = (bits ushr 32).toInt()
        copies[EXTRAFRAME_OFFSET + 15] = (bits and 0xFFFFFFFFL).toInt()
      }
      nextBIndex = HASHBYTES_TO_USE // skipping remaining random bits
    }
    state = NEXT_BYTES
    if (bytes.size == 0) {
      return
    }
    var nextByteToReturn = 0 // index of ready bytes in "bytes" array
    // possibly not all of HASHBYTES_TO_USE bytes were used previous time
    n = if ((HASHBYTES_TO_USE - nextBIndex) < (bytes.size - nextByteToReturn))
      (HASHBYTES_TO_USE
          - nextBIndex)
    else
      bytes.size - nextByteToReturn
    if (n > 0) {
      System.arraycopy(nextBytes, nextBIndex, bytes, nextByteToReturn, n)
      nextBIndex += n
      nextByteToReturn += n
    }
    if (nextByteToReturn >= bytes.size) {
      return  // return because "bytes[]" are filled in
    }
    n = seed[BYTES_OFFSET] and 0x03
    while (true) {
      if (n == 0) {
        seed[lastWord] = (counter ushr 32).toInt()
        seed[lastWord + 1] = (counter and 0xFFFFFFFFL).toInt()
        seed[lastWord + 2] = END_FLAGS[0]
      } else {
        seed[lastWord] = seed[lastWord] or ((counter ushr RIGHT1[n]) and MASK[n].toLong()).toInt()
        seed[lastWord + 1] = ((counter ushr RIGHT2[n]) and 0xFFFFFFFFL).toInt()
        seed[lastWord + 2] = ((counter shl LEFT[n]) or END_FLAGS[n].toLong()).toInt()
      }
      if (seed[BYTES_OFFSET] > MAX_BYTES) {
        copies[EXTRAFRAME_OFFSET] = seed[FRAME_LENGTH]
        copies[EXTRAFRAME_OFFSET + 1] = seed[FRAME_LENGTH + 1]
      }
      computeHash(seed)
      if (seed[BYTES_OFFSET] > MAX_BYTES) {
        System.arraycopy(seed, 0, copies, FRAME_OFFSET, FRAME_LENGTH)
        System.arraycopy(
          copies, EXTRAFRAME_OFFSET, seed, 0,
          FRAME_LENGTH
        )
        computeHash(seed)
        System.arraycopy(copies, FRAME_OFFSET, seed, 0, FRAME_LENGTH)
      }
      counter++
      var j = 0
      i = 0
      while (i < EXTRAFRAME_OFFSET) {
        val k = seed[HASH_OFFSET + i]
        nextBytes[j] = (k ushr 24).toByte() // getting first  byte from left
        nextBytes[j + 1] = (k ushr 16).toByte() // getting second byte from left
        nextBytes[j + 2] = (k ushr 8).toByte() // getting third  byte from left
        nextBytes[j + 3] = (k).toByte() // getting fourth byte from left
        j += 4
        i++
      }
      nextBIndex = 0
      j = if (HASHBYTES_TO_USE < (bytes.size - nextByteToReturn))
        HASHBYTES_TO_USE
      else
        bytes.size - nextByteToReturn
      if (j > 0) {
        System.arraycopy(nextBytes, 0, bytes, nextByteToReturn, j)
        nextByteToReturn += j
        nextBIndex += j
      }
      if (nextByteToReturn >= bytes.size) {
        break
      }
    }
  }

  companion object {
    /**
     * Only public method. Derive a key from the given seed.
     *
     *
     * Use this method only to retrieve encrypted data that couldn't be retrieved otherwise.
     *
     * @param seed           seed used for the random generator, usually coming from a password
     * @param keySizeInBytes length of the array returned
     */
    @JvmStatic
    fun deriveInsecureKey(seed: ByteArray, keySizeInBytes: Int): ByteArray {
      val derivator = InsecureSHA1PRNGKeyDerivator()
      derivator.setSeed(seed)
      val key = ByteArray(keySizeInBytes)
      derivator.nextBytes(key)
      return key
    }

    // constants to use in expressions operating on bytes in int and long variables:
    // END_FLAGS - final bytes in words to append to message;
    //             see "ch.5.1 Padding the Message, FIPS 180-2"
    // RIGHT1    - shifts to right for left half of long
    // RIGHT2    - shifts to right for right half of long
    // LEFT      - shifts to left for bytes
    // MASK      - mask to select counter's bytes after shift to right
    private val END_FLAGS = intArrayOf(-0x80000000, 0x800000, 0x8000, 0x80)
    private val RIGHT1 = intArrayOf(0, 40, 48, 56)
    private val RIGHT2 = intArrayOf(0, 8, 16, 24)
    private val LEFT = intArrayOf(0, 24, 16, 8)
    private val MASK = intArrayOf(
      -0x1, 0x00FFFFFF, 0x0000FFFF,
      0x000000FF
    )

    // HASHBYTES_TO_USE defines # of bytes returned by "computeHash(byte[])"
    // to use to form byte array returning by the "nextBytes(byte[])" method
    // Note, that this implementation uses more bytes than it is defined
    // in the above specification.
    private const val HASHBYTES_TO_USE = 20

    // value of 16 defined in the "SECURE HASH STANDARD", FIPS PUB 180-2
    private const val FRAME_LENGTH = 16

    // miscellaneous constants defined in this implementation:
    // COUNTER_BASE - initial value to set to "counter" before computing "nextBytes(..)";
    //                note, that the exact value is not defined in STANDARD
    // HASHCOPY_OFFSET   - offset for copy of current hash in "copies" array
    // EXTRAFRAME_OFFSET - offset for extra frame in "copies" array;
    //                     as the extra frame follows the current hash frame,
    //                     EXTRAFRAME_OFFSET is equal to length of current hash frame
    // FRAME_OFFSET      - offset for frame in "copies" array
    // MAX_BYTES - maximum # of seed bytes processing which doesn't require extra frame
    //             see (1) comments on usage of "seed" array below and
    //             (2) comments in "engineNextBytes(byte[])" method
    //
    // UNDEFINED  - three states of engine; initially its state is "UNDEFINED"
    // SET_SEED     call to "engineSetSeed"  sets up "SET_SEED" state,
    // NEXT_BYTES   call to "engineNextByte" sets up "NEXT_BYTES" state
    private const val COUNTER_BASE = 0
    private const val HASHCOPY_OFFSET = 0
    private const val EXTRAFRAME_OFFSET = 5
    private const val FRAME_OFFSET = 21
    private const val MAX_BYTES = 48
    private const val UNDEFINED = 0
    private const val SET_SEED = 1
    private const val NEXT_BYTES = 2

    /**
     * constant defined in "SECURE HASH STANDARD"
     */
    private const val H0 = 0x67452301

    /**
     * constant defined in "SECURE HASH STANDARD"
     */
    private const val H1 = -0x10325477

    /**
     * constant defined in "SECURE HASH STANDARD"
     */
    private const val H2 = -0x67452302

    /**
     * constant defined in "SECURE HASH STANDARD"
     */
    private const val H3 = 0x10325476

    /**
     * constant defined in "SECURE HASH STANDARD"
     */
    private const val H4 = -0x3c2d1e10

    /**
     * offset in buffer to store number of bytes in 0-15 word frame
     */
    private const val BYTES_OFFSET = 81

    /**
     * offset in buffer to store current hash value
     */
    private const val HASH_OFFSET = 82

    /**
     * # of bytes in H0-H4 words; <BR></BR>
     * in this implementation # is set to 20 (in general # varies from 1 to 20)
     */
    private const val DIGEST_LENGTH = 20

    /**
     * The method generates a 160 bit hash value using
     * a 512 bit message stored in first 16 words of int[] array argument and
     * current hash value stored in five words, beginning OFFSET+1, of the array argument.
     * Computation is done according to SHA-1 algorithm.
     *
     *
     * The resulting hash value replaces the previous hash value in the array;
     * original bits of the message are not preserved.
     *
     *
     * No checks on argument supplied, that is,
     * a calling method is responsible for such checks.
     * In case of incorrect array passed to the method
     * either NPE or IndexOutOfBoundException gets thrown by JVM.
     *
     * @params arrW - integer array; arrW.length >= (BYTES_OFFSET+6); <BR></BR>
     * only first (BYTES_OFFSET+6) words are used
     */
    private fun computeHash(arrW: IntArray) {
      var a = arrW[HASH_OFFSET]
      var b = arrW[HASH_OFFSET + 1]
      var c = arrW[HASH_OFFSET + 2]
      var d = arrW[HASH_OFFSET + 3]
      var e = arrW[HASH_OFFSET + 4]
      var temp: Int
      // In this implementation the "d. For t = 0 to 79 do" loop
      // is split into four loops. The following constants:
      //     K = 5A827999   0 <= t <= 19
      //     K = 6ED9EBA1  20 <= t <= 39
      //     K = 8F1BBCDC  40 <= t <= 59
      //     K = CA62C1D6  60 <= t <= 79
      // are hex literals in the loops.
      for (t in 16..79) {
        temp = arrW[t - 3] xor arrW[t - 8] xor arrW[t - 14] xor arrW[t - 16]
        arrW[t] = (temp shl 1) or (temp ushr 31)
      }
      for (t in 0..19) {
        temp = ((a shl 5) or (a ushr 27)) +
            ((b and c) or ((b.inv()) and d)) +
            (e + arrW[t] + 0x5A827999)
        e = d
        d = c
        c = (b shl 30) or (b ushr 2)
        b = a
        a = temp
      }
      for (t in 20..39) {
        temp = (((a shl 5) or (a ushr 27))) + (b xor c xor d) + (e + arrW[t] + 0x6ED9EBA1)
        e = d
        d = c
        c = (b shl 30) or (b ushr 2)
        b = a
        a = temp
      }
      for (t in 40..59) {
        temp = ((a shl 5) or (a ushr 27)) + ((b and c) or (b and d) or (c and d)) +
            (e + arrW[t] + -0x70e44324)
        e = d
        d = c
        c = (b shl 30) or (b ushr 2)
        b = a
        a = temp
      }
      for (t in 60..79) {
        temp = (((a shl 5) or (a ushr 27))) + (b xor c xor d) + (e + arrW[t] + -0x359d3e2a)
        e = d
        d = c
        c = (b shl 30) or (b ushr 2)
        b = a
        a = temp
      }
      arrW[HASH_OFFSET] += a
      arrW[HASH_OFFSET + 1] += b
      arrW[HASH_OFFSET + 2] += c
      arrW[HASH_OFFSET + 3] += d
      arrW[HASH_OFFSET + 4] += e
    }

    /**
     * The method appends new bytes to existing ones
     * within limit of a frame of 64 bytes (16 words).
     *
     *
     * Once a length of accumulated bytes reaches the limit
     * the "computeHash(int[])" method is invoked on the array to compute updated hash,
     * and the number of bytes in the frame is set to 0.
     * Thus, after appending all bytes, the array contain only those bytes
     * that were not used in computing final hash value yet.
     *
     *
     * No checks on arguments passed to the method, that is,
     * a calling method is responsible for such checks.
     *
     * @params intArray  - int array containing bytes to which to append;
     * intArray.length >= (BYTES_OFFSET+6)
     * @params byteInput - array of bytes to use for the getUpdateInfo
     * @params from      - the offset to start in the "byteInput" array
     * @params to        - a number of the last byte in the input array to use,
     * that is, for first byte "to"==0, for last byte "to"==input.length-1
     */
    private fun updateHash(intArray: IntArray, byteInput: ByteArray, fromByte: Int, toByte: Int) {
      // As intArray contains a packed bytes
      // the buffer's index is in the intArray[BYTES_OFFSET] element
      val index = intArray[BYTES_OFFSET]
      var i = fromByte
      var wordIndex = index shr 2
      var byteIndex = index and 0x03
      intArray[BYTES_OFFSET] = (index + toByte - fromByte + 1) and 63
      // In general case there are 3 stages :
      // - appending bytes to non-full word,
      // - writing 4 bytes into empty words,
      // - writing less than 4 bytes in last word
      if (byteIndex != 0) {       // appending bytes in non-full word (as if)
        while ((i <= toByte) && (byteIndex < 4)) {
          intArray[wordIndex] =
            intArray[wordIndex] or ((byteInput[i].toInt() and 0xFF) shl ((3 - byteIndex) shl 3))
          byteIndex++
          i++
        }
        if (byteIndex == 4) {
          wordIndex++
          if (wordIndex == 16) {          // intArray is full, computing hash
            computeHash(intArray)
            wordIndex = 0
          }
        }
        if (i > toByte) {                 // all input bytes appended
          return
        }
      }
      // writing full words
      val maxWord = (toByte - i + 1) shr 2 // # of remaining full words, may be "0"
      for (k in 0 until maxWord) {
        intArray[wordIndex] = ((byteInput[i].toInt() and 0xFF) shl 24) or
            ((byteInput[i + 1].toInt() and 0xFF) shl 16) or
            ((byteInput[i + 2].toInt() and 0xFF) shl 8) or
            ((byteInput[i + 3].toInt() and 0xFF))
        i += 4
        wordIndex++
        if (wordIndex < 16) {     // buffer is not full yet
          continue
        }
        computeHash(intArray) // buffer is full, computing hash
        wordIndex = 0
      }
      // writing last incomplete word
      // after writing free byte positions are set to "0"s
      val nBytes = toByte - i + 1
      if (nBytes != 0) {
        var w = (byteInput[i].toInt() and 0xFF) shl 24
        if (nBytes != 1) {
          w = w or ((byteInput[i + 1].toInt() and 0xFF) shl 16)
          if (nBytes != 2) {
            w = w or ((byteInput[i + 2].toInt() and 0xFF) shl 8)
          }
        }
        intArray[wordIndex] = w
      }
      return
    }
  }
}
