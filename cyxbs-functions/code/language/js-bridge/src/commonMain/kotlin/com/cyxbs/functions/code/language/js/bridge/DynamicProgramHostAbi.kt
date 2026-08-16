package com.cyxbs.functions.code.language.js.bridge

/**
 * 动态语言用户程序与端上宿主之间使用的稳定 JavaScript ABI。
 *
 * 语言包只可使用这里声明的函数名生成运行时代码，不能依赖 QuickJS、Kotlin 对象名或编辑器实现。
 * 这些函数由每次运行创建的隔离 Runtime 注入，用户源码本身不应直接引用它们。
 */
object DynamicProgramHostAbi {

  /**
   * 向标准输出追加一段已经完成语言自身字符串转换的文本，不自动补换行。
   *
   * 这是由 [OUTPUT_BRIDGE_SOURCE] 安装的 JavaScript 函数，不会把原始 Unicode 文本直接传给宿主。
   */
  const val WRITE_STANDARD_OUTPUT: String = "__cyxbs_write_stdout"

  /**
   * 向标准错误追加一段已经完成语言自身字符串转换的文本，不自动补换行。
   *
   * 这是由 [OUTPUT_BRIDGE_SOURCE] 安装的 JavaScript 函数，不会把原始 Unicode 文本直接传给宿主。
   */
  const val WRITE_STANDARD_ERROR: String = "__cyxbs_write_stderr"

  /** 宿主接收标准输出 UTF-8 Base64 小块的私有函数名，语言包不得直接调用。 */
  const val WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK: String =
    "__cyxbs_write_stdout_utf8_base64_chunk"

  /** 宿主接收标准错误 UTF-8 Base64 小块的私有函数名，语言包不得直接调用。 */
  const val WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK: String =
    "__cyxbs_write_stderr_utf8_base64_chunk"

  /** 每次跨 Runtime 边界最多传输的已解码输出字节数。 */
  const val MAX_OUTPUT_UTF8_CHUNK_BYTES: Int = 4 * 1024

  /**
   * 返回本次预加载标准输入的 UTF-8 Base64 文本。
   *
   * Runner 初始化时会把同名宿主 getter 捕获进闭包，并用忽略实参的 JS 零参包装器替换该全局名。
   * 因此用户即使误传大参数也不会跨宿主边界。返回值只包含 ASCII，规避部分 JS 引擎 JNI/Native
   * 字符串边界对 supplementary 字符的 Modified-UTF8 损坏。
   */
  const val READ_STANDARD_INPUT_UTF8_BASE64: String = "__cyxbs_read_stdin_utf8_base64"

  /** [STANDARD_INPUT_READER_SOURCE] 定义的 UTF-8 Base64 解码函数名。 */
  const val DECODE_UTF8_BASE64: String = "__cyxbs_decode_utf8_base64"

  /** [STANDARD_INPUT_READER_SOURCE] 定义的完整标准输入文本读取函数名。 */
  const val READ_STANDARD_INPUT_TEXT: String = "__cyxbs_read_standard_input_text"

  /**
   * Runner 在执行用户 Module 前安装的输出桥。
   *
   * 用户程序与语言 intrinsic 仍传递普通 JavaScript 字符串；桥在 JS 内按 Unicode 标量编码 UTF-8，
   * 并以不超过 [MAX_OUTPUT_UTF8_CHUNK_BYTES] 的 Base64 小块进入宿主。这样既规避部分引擎的
   * Modified-UTF8 supplementary 字符损坏，也避免超大用户字符串在一次宿主调用中产生同量级副本。
   * `console` 同样在 JS 内完成基础格式化并复用该通道；孤立 UTF-16 代理稳定替换为 U+FFFD。
   */
  val OUTPUT_BRIDGE_SOURCE: String = """
    (function () {
      const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
      const maxChunkBytes = $MAX_OUTPUT_UTF8_CHUNK_BYTES;
      const reflectApply = Reflect.apply;
      const stringConstructor = String;
      const stringCharCodeAt = String.prototype.charCodeAt;
      const arrayJoin = Array.prototype.join;
      const stdoutConsumer = globalThis.$WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK;
      const stderrConsumer = globalThis.$WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK;
      const standardInputGetter = globalThis.$READ_STANDARD_INPUT_UTF8_BASE64;
      // 用户 Module 执行前移除原始宿主 capability；公开包装器仅持有不可覆盖的闭包引用。
      delete globalThis.$WRITE_STANDARD_OUTPUT_UTF8_BASE64_CHUNK;
      delete globalThis.$WRITE_STANDARD_ERROR_UTF8_BASE64_CHUNK;
      delete globalThis.$READ_STANDARD_INPUT_UTF8_BASE64;
      if (typeof stdoutConsumer !== "function" || typeof stderrConsumer !== "function" ||
          typeof standardInputGetter !== "function") {
        throw new Error("Dynamic program host bridge is unavailable");
      }

      function encodeBase64(bytes) {
        let result = "";
        for (let index = 0; index < bytes.length; index += 3) {
          const first = bytes[index];
          const hasSecond = index + 1 < bytes.length;
          const hasThird = index + 2 < bytes.length;
          const second = hasSecond ? bytes[index + 1] : 0;
          const third = hasThird ? bytes[index + 2] : 0;
          const value = (first << 16) | (second << 8) | third;
          result += alphabet[(value >>> 18) & 63];
          result += alphabet[(value >>> 12) & 63];
          result += hasSecond ? alphabet[(value >>> 6) & 63] : "=";
          result += hasThird ? alphabet[value & 63] : "=";
        }
        return result;
      }

      function writeUtf8(text, consumer) {
        text = stringConstructor(text);
        let bytes = [];
        function flush() {
          if (bytes.length === 0) return;
          consumer(encodeBase64(bytes));
          bytes = [];
        }
        function appendCodePoint(codePoint) {
          const byteCount = codePoint <= 127 ? 1 : codePoint <= 2047 ? 2 : codePoint <= 65535 ? 3 : 4;
          if (bytes.length + byteCount > maxChunkBytes) flush();
          if (byteCount === 1) {
            bytes[bytes.length] = codePoint;
          } else if (byteCount === 2) {
            bytes[bytes.length] = 192 | (codePoint >>> 6);
            bytes[bytes.length] = 128 | (codePoint & 63);
          } else if (byteCount === 3) {
            bytes[bytes.length] = 224 | (codePoint >>> 12);
            bytes[bytes.length] = 128 | ((codePoint >>> 6) & 63);
            bytes[bytes.length] = 128 | (codePoint & 63);
          } else {
            bytes[bytes.length] = 240 | (codePoint >>> 18);
            bytes[bytes.length] = 128 | ((codePoint >>> 12) & 63);
            bytes[bytes.length] = 128 | ((codePoint >>> 6) & 63);
            bytes[bytes.length] = 128 | (codePoint & 63);
          }
        }

        for (let index = 0; index < text.length; index++) {
          const first = reflectApply(stringCharCodeAt, text, [index]);
          if (first >= 55296 && first <= 56319) {
            const second = index + 1 < text.length
              ? reflectApply(stringCharCodeAt, text, [index + 1])
              : -1;
            if (second >= 56320 && second <= 57343) {
              appendCodePoint(65536 + ((first - 55296) << 10) + (second - 56320));
              index++;
            } else {
              appendCodePoint(65533);
            }
          } else if (first >= 56320 && first <= 57343) {
            appendCodePoint(65533);
          } else {
            appendCodePoint(first);
          }
        }
        flush();
      }

      function formatConsoleArgument(value) {
        return value === null ? "null" : stringConstructor(value);
      }
      function writeConsoleLine(consumer, args) {
        const values = [];
        for (let index = 0; index < args.length; index++) {
          values[values.length] = formatConsoleArgument(args[index]);
        }
        writeUtf8(reflectApply(arrayJoin, values, [" "]) + "\n", consumer);
      }

      globalThis.$WRITE_STANDARD_OUTPUT = function (text) {
        writeUtf8(text === null ? "null" : text, stdoutConsumer);
      };
      globalThis.$WRITE_STANDARD_ERROR = function (text) {
        writeUtf8(text === null ? "null" : text, stderrConsumer);
      };
      globalThis.$READ_STANDARD_INPUT_UTF8_BASE64 = function () {
        return standardInputGetter();
      };
      globalThis.console = {
        log: function () { writeConsoleLine(stdoutConsumer, arguments); },
        info: function () { writeConsoleLine(stdoutConsumer, arguments); },
        warn: function () { writeConsoleLine(stderrConsumer, arguments); },
        error: function () { writeConsoleLine(stderrConsumer, arguments); },
      };
    })();
  """.trimIndent()

  /**
   * 语言运行时可直接嵌入的标准输入读取 helper。
   *
   * helper 不依赖浏览器 `atob` 或 `TextDecoder`，可在 QuickJS 等最小 ES Runtime 中工作；它先从
   * [READ_STANDARD_INPUT_UTF8_BASE64] 获取 ASCII，再严格解码 UTF-8，并恢复 supplementary 字符的
   * UTF-16 代理对。输入由宿主生成，因此格式异常表示 ABI 损坏并会直接抛错。
   */
  val STANDARD_INPUT_READER_SOURCE: String = """
    const __cyxbs_standard_input_base64_reader__ = globalThis.$READ_STANDARD_INPUT_UTF8_BASE64;
    const $DECODE_UTF8_BASE64 = (function () {
      const reflectApply = Reflect.apply;
      const stringIndexOf = String.prototype.indexOf;
      const stringFromCharCode = String.fromCharCode;
      const arrayJoin = Array.prototype.join;

      return function (base64) {
      const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
      if (typeof base64 !== "string" || (base64.length & 3) !== 0) {
        throw new Error("Invalid UTF-8 Base64 standard input payload");
      }
      const bytes = [];
      for (let index = 0; index < base64.length; index += 4) {
        const first = reflectApply(stringIndexOf, alphabet, [base64[index]]);
        const second = reflectApply(stringIndexOf, alphabet, [base64[index + 1]]);
        const thirdPadding = base64[index + 2] === "=";
        const fourthPadding = base64[index + 3] === "=";
        const third = thirdPadding ? 0 : reflectApply(stringIndexOf, alphabet, [base64[index + 2]]);
        const fourth = fourthPadding ? 0 : reflectApply(stringIndexOf, alphabet, [base64[index + 3]]);
        if (first < 0 || second < 0 || third < 0 || fourth < 0 ||
            thirdPadding && !fourthPadding ||
            (thirdPadding || fourthPadding) && index + 4 !== base64.length ||
            thirdPadding && (second & 15) !== 0 ||
            !thirdPadding && fourthPadding && (third & 3) !== 0) {
          throw new Error("Invalid UTF-8 Base64 standard input payload");
        }
        const value = (first << 18) | (second << 12) | (third << 6) | fourth;
        bytes[bytes.length] = (value >>> 16) & 255;
        if (!thirdPadding) bytes[bytes.length] = (value >>> 8) & 255;
        if (!fourthPadding) bytes[bytes.length] = value & 255;
      }

      const resultChunks = [];
      const codeUnits = [];
      function flushCodeUnits() {
        if (codeUnits.length === 0) return;
        resultChunks[resultChunks.length] = reflectApply(stringFromCharCode, null, codeUnits);
        codeUnits.length = 0;
      }
      for (let index = 0; index < bytes.length;) {
        const first = bytes[index++];
        let codePoint;
        if (first <= 127) {
          codePoint = first;
        } else if (first >= 194 && first <= 223 && index < bytes.length) {
          const second = bytes[index++];
          if ((second & 192) !== 128) throw new Error("Invalid UTF-8 standard input payload");
          codePoint = ((first & 31) << 6) | (second & 63);
        } else if (first >= 224 && first <= 239 && index + 1 < bytes.length) {
          const second = bytes[index++];
          const third = bytes[index++];
          if ((second & 192) !== 128 || (third & 192) !== 128 ||
              first === 224 && second < 160 || first === 237 && second >= 160) {
            throw new Error("Invalid UTF-8 standard input payload");
          }
          codePoint = ((first & 15) << 12) | ((second & 63) << 6) | (third & 63);
        } else if (first >= 240 && first <= 244 && index + 2 < bytes.length) {
          const second = bytes[index++];
          const third = bytes[index++];
          const fourth = bytes[index++];
          if ((second & 192) !== 128 || (third & 192) !== 128 || (fourth & 192) !== 128 ||
              first === 240 && second < 144 || first === 244 && second >= 144) {
            throw new Error("Invalid UTF-8 standard input payload");
          }
          codePoint = ((first & 7) << 18) | ((second & 63) << 12) |
            ((third & 63) << 6) | (fourth & 63);
        } else {
          throw new Error("Invalid UTF-8 standard input payload");
        }
        if (codePoint <= 65535) {
          codeUnits[codeUnits.length] = codePoint;
        } else {
          codePoint -= 65536;
          codeUnits[codeUnits.length] = 55296 + (codePoint >>> 10);
          codeUnits[codeUnits.length] = 56320 + (codePoint & 1023);
        }
        if (codeUnits.length >= 4096) flushCodeUnits();
      }
      flushCodeUnits();
      return reflectApply(arrayJoin, resultChunks, [""]);
      };
    })();

    function $READ_STANDARD_INPUT_TEXT() {
      return $DECODE_UTF8_BASE64(__cyxbs_standard_input_base64_reader__());
    }
  """.trimIndent()
}
