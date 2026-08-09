package com.klinetrain.app.formula

import com.klinetrain.app.data.model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 公式解析/计算异常，message 为中文错误描述(带行号) */
class FormulaException(message: String) : Exception(message)

/**
 * 通达信风格自定义公式指标引擎。
 *
 * 语法：
 * - 每行一条语句："名称: 表达式;"(输出一条线) 或 "名称 := 表达式;"(中间变量，不输出)，分号可省略
 * - 支持 // 行注释，一行内可用 ';' 写多条语句
 * - 变量: OPEN/O, HIGH/H, LOW/L, CLOSE/C, VOL/V/VOLUME, AMOUNT (大小写不敏感)
 * - 函数: MA EMA SMA REF HHV LLV SUM STD ABS MAX MIN CROSS IF COUNT AVEDEV
 * - 运算: + - * / 一元负号、> < >= <= == !=、AND/OR/&&/||、括号
 *
 * 实现：递归下降解析 -> AST -> 对整列序列求值(标量自动广播)。
 * 除以0、数据不足(暖机期)、无效结果处为 null。
 */
object FormulaEngine {

    // ==================== 公开 API ====================

    /**
     * 计算公式。返回 线名 -> 序列(与 klines 等长, 暖机期/无效处为 null)。
     * 出错抛出 [FormulaException]，错误信息为中文并带行号。
     */
    fun evaluate(source: String, klines: List<Kline>): Map<String, List<Double?>> {
        val program = parseAndCheck(source)
        return evalProgram(program, klines)
    }

    /** 校验公式。返回 null 表示通过，否则返回中文错误描述。 */
    fun validate(source: String): String? {
        return try {
            val program = parseAndCheck(source)
            // 用一小段合成数据实际跑一遍，能捕获"周期参数必须是常数"这类运行期错误
            evalProgram(program, syntheticKlines())
            null
        } catch (e: FormulaException) {
            e.message ?: "公式存在错误"
        }
    }

    // ==================== 词法分析 ====================

    private enum class TokType { NUM, IDENT, OP, LPAREN, RPAREN, COMMA, END }

    private data class Token(val type: TokType, val text: String, val num: Double = 0.0)

    private val TWO_CHAR_OPS = setOf(">=", "<=", "==", "!=", "&&", "||")
    private val CMP_OPS = setOf(">", "<", ">=", "<=", "==", "!=")

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char) = c.isLetter() || c.isDigit() || c == '_'

    private fun lex(expr: String, line: Int): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < expr.length && expr[i + 1].isDigit()) -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    val text = expr.substring(start, i)
                    val v = text.toDoubleOrNull()
                        ?: throw FormulaException("第${line}行: 非法数字 \"$text\"")
                    tokens.add(Token(TokType.NUM, text, v))
                }
                isIdentStart(c) -> {
                    val start = i
                    while (i < expr.length && isIdentPart(expr[i])) i++
                    val text = expr.substring(start, i)
                    when (text.uppercase()) {
                        "AND" -> tokens.add(Token(TokType.OP, "&&"))
                        "OR" -> tokens.add(Token(TokType.OP, "||"))
                        else -> tokens.add(Token(TokType.IDENT, text))
                    }
                }
                c == '(' -> { tokens.add(Token(TokType.LPAREN, "(")); i++ }
                c == ')' -> { tokens.add(Token(TokType.RPAREN, ")")); i++ }
                c == ',' -> { tokens.add(Token(TokType.COMMA, ",")); i++ }
                else -> {
                    val two = if (i + 1 < expr.length) expr.substring(i, i + 2) else ""
                    when {
                        two in TWO_CHAR_OPS -> { tokens.add(Token(TokType.OP, two)); i += 2 }
                        c == '=' -> { tokens.add(Token(TokType.OP, "==")); i++ }
                        c == '+' || c == '-' || c == '*' || c == '/' || c == '>' || c == '<' -> {
                            tokens.add(Token(TokType.OP, c.toString())); i++
                        }
                        else -> throw FormulaException("第${line}行: 非法字符 '$c'")
                    }
                }
            }
        }
        tokens.add(Token(TokType.END, ""))
        return tokens
    }

    // ==================== AST ====================

    private sealed class Node {
        class Num(val v: Double) : Node()
        class Var(val name: String, val line: Int) : Node()
        class Neg(val child: Node) : Node()
        class Bin(val op: String, val l: Node, val r: Node) : Node()
        class Call(val name: String, val args: List<Node>, val line: Int) : Node()
    }

    private class Stmt(val name: String, val isOutput: Boolean, val expr: Node, val line: Int)

    // ==================== 语法分析(递归下降) ====================

    private class Parser(private val tokens: List<Token>, private val line: Int) {
        private var pos = 0

        private fun peek(): Token = tokens[pos]
        private fun next(): Token = tokens[pos++]
        private fun opIs(op: String): Boolean =
            peek().type == TokType.OP && peek().text == op

        fun parse(): Node {
            val node = parseOr()
            if (peek().type != TokType.END) {
                throw FormulaException("第${line}行: 表达式末尾有多余内容 \"${peek().text}\"")
            }
            return node
        }

        private fun parseOr(): Node {
            var l = parseAnd()
            while (opIs("||")) { next(); l = Node.Bin("||", l, parseAnd()) }
            return l
        }

        private fun parseAnd(): Node {
            var l = parseCmp()
            while (opIs("&&")) { next(); l = Node.Bin("&&", l, parseCmp()) }
            return l
        }

        private fun parseCmp(): Node {
            var l = parseAdd()
            while (peek().type == TokType.OP && peek().text in CMP_OPS) {
                val op = next().text
                l = Node.Bin(op, l, parseAdd())
            }
            return l
        }

        private fun parseAdd(): Node {
            var l = parseMul()
            while (opIs("+") || opIs("-")) {
                val op = next().text
                l = Node.Bin(op, l, parseMul())
            }
            return l
        }

        private fun parseMul(): Node {
            var l = parseUnary()
            while (opIs("*") || opIs("/")) {
                val op = next().text
                l = Node.Bin(op, l, parseUnary())
            }
            return l
        }

        private fun parseUnary(): Node = when {
            opIs("-") -> { next(); Node.Neg(parseUnary()) }
            opIs("+") -> { next(); parseUnary() }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Node {
            val t = next()
            return when (t.type) {
                TokType.NUM -> Node.Num(t.num)
                TokType.IDENT -> {
                    if (peek().type == TokType.LPAREN) {
                        next() // 吃掉 '('
                        Node.Call(t.text, parseArgs(t.text), line)
                    } else {
                        Node.Var(t.text, line)
                    }
                }
                TokType.LPAREN -> {
                    val node = parseOr()
                    if (peek().type != TokType.RPAREN) {
                        throw FormulaException("第${line}行: 缺少右括号 \")\"")
                    }
                    next()
                    node
                }
                TokType.END -> throw FormulaException("第${line}行: 表达式不完整")
                else -> throw FormulaException("第${line}行: 意外的符号 \"${t.text}\"")
            }
        }

        private fun parseArgs(fname: String): List<Node> {
            val args = ArrayList<Node>()
            if (peek().type == TokType.RPAREN) { next(); return args }
            while (true) {
                args.add(parseOr())
                val t = next()
                when (t.type) {
                    TokType.RPAREN -> return args
                    TokType.COMMA -> { /* 继续下一个参数 */ }
                    else -> throw FormulaException("第${line}行: 函数 ${fname.uppercase()} 的参数缺少\",\"或右括号")
                }
            }
        }
    }

    // ==================== 语句解析 + 语义检查 ====================

    private val BUILTIN_VARS = setOf(
        "OPEN", "O", "HIGH", "H", "LOW", "L", "CLOSE", "C", "VOL", "V", "VOLUME", "AMOUNT"
    )

    /** 函数名 -> 参数个数 */
    private val FUNCTIONS: Map<String, Int> = mapOf(
        "MA" to 2, "EMA" to 2, "SMA" to 3, "REF" to 2, "HHV" to 2, "LLV" to 2,
        "SUM" to 2, "STD" to 2, "ABS" to 1, "MAX" to 2, "MIN" to 2,
        "CROSS" to 2, "IF" to 3, "COUNT" to 2, "AVEDEV" to 2
    )

    private fun isValidName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!(name[0].isLetter() || name[0] == '_')) return false
        return name.all { it.isLetter() || it.isDigit() || it == '_' }
    }

    private fun parseAndCheck(source: String): List<Stmt> {
        if (source.isBlank()) throw FormulaException("公式为空，请输入至少一条 \"名称:表达式\"")
        val stmts = ArrayList<Stmt>()
        val lines = source.split('\n')
        for ((idx, raw) in lines.withIndex()) {
            val lineNo = idx + 1
            var text = raw
            val comment = text.indexOf("//")
            if (comment >= 0) text = text.substring(0, comment)
            for (part in text.split(';')) {
                val stmt = part.trim()
                if (stmt.isEmpty()) continue
                val colonIdx = stmt.indexOf(':')
                if (colonIdx < 0) {
                    throw FormulaException("第${lineNo}行: 缺少\":\" (格式: 名称:表达式 或 名称:=表达式)")
                }
                val isAssign = colonIdx + 1 < stmt.length && stmt[colonIdx + 1] == '='
                val name = stmt.substring(0, colonIdx).trim()
                val exprText = stmt.substring(colonIdx + if (isAssign) 2 else 1).trim()
                if (!isValidName(name)) {
                    throw FormulaException("第${lineNo}行: 无效的名称 \"$name\"")
                }
                if (exprText.isEmpty()) {
                    throw FormulaException("第${lineNo}行: \"$name\" 缺少表达式")
                }
                val node = Parser(lex(exprText, lineNo), lineNo).parse()
                stmts.add(Stmt(name, !isAssign, node, lineNo))
            }
        }
        if (stmts.isEmpty()) throw FormulaException("公式为空，请输入至少一条 \"名称:表达式\"")
        if (stmts.none { it.isOutput }) {
            throw FormulaException("公式没有输出行，请至少用 \"名称:表达式\" 定义一条输出线")
        }
        checkProgram(stmts)
        return stmts
    }

    private fun checkProgram(stmts: List<Stmt>) {
        val defined = HashSet<String>()
        for (s in stmts) {
            checkNode(s.expr, defined)
            defined.add(s.name.uppercase())
        }
    }

    private fun checkNode(node: Node, defined: Set<String>) {
        when (node) {
            is Node.Num -> {}
            is Node.Var -> {
                val u = node.name.uppercase()
                if (u !in BUILTIN_VARS && u !in defined) {
                    throw FormulaException("第${node.line}行: 未知变量 ${node.name}")
                }
            }
            is Node.Neg -> checkNode(node.child, defined)
            is Node.Bin -> { checkNode(node.l, defined); checkNode(node.r, defined) }
            is Node.Call -> {
                val arity = FUNCTIONS[node.name.uppercase()]
                    ?: throw FormulaException("第${node.line}行: 未知函数 ${node.name}")
                if (node.args.size != arity) {
                    throw FormulaException(
                        "第${node.line}行: 函数 ${node.name.uppercase()} 需要${arity}个参数，实际${node.args.size}个"
                    )
                }
                node.args.forEach { checkNode(it, defined) }
            }
        }
    }

    // ==================== 求值 ====================

    /** 求值结果：标量或序列(标量参与序列运算时自动广播) */
    private sealed class Value {
        class Num(val v: Double) : Value()
        class Seq(val list: List<Double?>) : Value()
    }

    private fun clean(d: Double?): Double? = if (d != null && d.isFinite()) d else null

    private fun toList(v: Value, size: Int): List<Double?> = when (v) {
        is Value.Num -> { val c = clean(v.v); List(size) { c } }
        is Value.Seq -> v.list
    }

    private fun evalProgram(stmts: List<Stmt>, klines: List<Kline>): Map<String, List<Double?>> {
        val size = klines.size
        val env = HashMap<String, Value>()
        val opens = Value.Seq(klines.map { clean(it.open) })
        val highs = Value.Seq(klines.map { clean(it.high) })
        val lows = Value.Seq(klines.map { clean(it.low) })
        val closes = Value.Seq(klines.map { clean(it.close) })
        val vols = Value.Seq(klines.map { clean(it.volume) })
        val amounts = Value.Seq(klines.map { clean(it.amount) })
        env["OPEN"] = opens; env["O"] = opens
        env["HIGH"] = highs; env["H"] = highs
        env["LOW"] = lows; env["L"] = lows
        env["CLOSE"] = closes; env["C"] = closes
        env["VOL"] = vols; env["V"] = vols; env["VOLUME"] = vols
        env["AMOUNT"] = amounts

        val out = LinkedHashMap<String, List<Double?>>()
        for (s in stmts) {
            val v = evalNode(s.expr, env, size)
            env[s.name.uppercase()] = v
            if (s.isOutput) out[s.name] = toList(v, size)
        }
        return out
    }

    private fun evalNode(node: Node, env: Map<String, Value>, size: Int): Value = when (node) {
        is Node.Num -> Value.Num(node.v)
        is Node.Var -> env[node.name.uppercase()]
            ?: throw FormulaException("第${node.line}行: 未知变量 ${node.name}")
        is Node.Neg -> when (val v = evalNode(node.child, env, size)) {
            is Value.Num -> Value.Num(-v.v)
            is Value.Seq -> Value.Seq(v.list.map { if (it == null) null else -it })
        }
        is Node.Bin -> {
            val l = evalNode(node.l, env, size)
            val r = evalNode(node.r, env, size)
            if (l is Value.Num && r is Value.Num) {
                Value.Num(binOp(node.op, clean(l.v), clean(r.v)) ?: Double.NaN)
            } else {
                val a = toList(l, size)
                val b = toList(r, size)
                Value.Seq(List(size) { i -> binOp(node.op, a[i], b[i]) })
            }
        }
        is Node.Call -> evalCall(node, env, size)
    }

    private fun binOp(op: String, a: Double?, b: Double?): Double? {
        if (a == null || b == null) return null
        val r = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) return null else a / b
            ">" -> if (a > b) 1.0 else 0.0
            "<" -> if (a < b) 1.0 else 0.0
            ">=" -> if (a >= b) 1.0 else 0.0
            "<=" -> if (a <= b) 1.0 else 0.0
            "==" -> if (abs(a - b) < 1e-9) 1.0 else 0.0
            "!=" -> if (abs(a - b) >= 1e-9) 1.0 else 0.0
            "&&" -> if (a != 0.0 && b != 0.0) 1.0 else 0.0
            "||" -> if (a != 0.0 || b != 0.0) 1.0 else 0.0
            else -> return null
        }
        return clean(r)
    }

    private fun evalCall(node: Node.Call, env: Map<String, Value>, size: Int): Value {
        val name = node.name.uppercase()

        fun seqArg(idx: Int): List<Double?> = toList(evalNode(node.args[idx], env, size), size)

        fun intArg(idx: Int, minValue: Int): Int {
            val v = evalNode(node.args[idx], env, size)
            val d = (v as? Value.Num)?.v
                ?: throw FormulaException("第${node.line}行: 函数 $name 的第${idx + 1}个参数(周期)必须是常数")
            if (!d.isFinite()) {
                throw FormulaException("第${node.line}行: 函数 $name 的第${idx + 1}个参数无效")
            }
            val n = d.toInt()
            if (n < minValue) {
                throw FormulaException("第${node.line}行: 函数 $name 的周期参数必须≥$minValue")
            }
            return n
        }

        val list: List<Double?> = when (name) {
            "MA" -> rolling(seqArg(0), intArg(1, 1)) { win -> win.sum() / win.size }
            "EMA" -> emaSeq(seqArg(0), intArg(1, 1))
            "SMA" -> smaSeq(seqArg(0), intArg(1, 1), intArg(2, 1))
            "REF" -> refSeq(seqArg(0), intArg(1, 0))
            "HHV" -> rolling(seqArg(0), intArg(1, 1)) { win -> win.max() }
            "LLV" -> rolling(seqArg(0), intArg(1, 1)) { win -> win.min() }
            "SUM" -> rolling(seqArg(0), intArg(1, 1)) { win -> win.sum() }
            "STD" -> rolling(seqArg(0), intArg(1, 1)) { win ->
                val m = win.average()
                sqrt(win.sumOf { (it - m) * (it - m) } / win.size)
            }
            "AVEDEV" -> rolling(seqArg(0), intArg(1, 1)) { win ->
                val m = win.average()
                win.sumOf { abs(it - m) } / win.size
            }
            "ABS" -> seqArg(0).map { if (it == null) null else abs(it) }
            "MAX" -> zipSeq(seqArg(0), seqArg(1)) { a, b -> max(a, b) }
            "MIN" -> zipSeq(seqArg(0), seqArg(1)) { a, b -> min(a, b) }
            "CROSS" -> crossSeq(seqArg(0), seqArg(1))
            "IF" -> ifSeq(seqArg(0), seqArg(1), seqArg(2))
            "COUNT" -> countSeq(seqArg(0), intArg(1, 1))
            else -> throw FormulaException("第${node.line}行: 未知函数 ${node.name}")
        }
        return Value.Seq(list)
    }

    // ---------- 序列函数实现 (窗口含 null 或数据不足 -> null) ----------

    private inline fun rolling(x: List<Double?>, n: Int, f: (List<Double>) -> Double): List<Double?> {
        val out = arrayOfNulls<Double>(x.size)
        for (i in x.indices) {
            if (i < n - 1) continue
            var ok = true
            val win = ArrayList<Double>(n)
            for (j in i - n + 1..i) {
                val v = x[j]
                if (v == null) { ok = false; break }
                win.add(v)
            }
            if (ok) out[i] = clean(f(win))
        }
        return out.toList()
    }

    private inline fun zipSeq(a: List<Double?>, b: List<Double?>, f: (Double, Double) -> Double): List<Double?> =
        a.indices.map { i ->
            val x = a[i]; val y = b[i]
            if (x == null || y == null) null else clean(f(x, y))
        }

    /** 与 Indicators.ema 一致: 首个有效值作为初值, null 处输出 null 但不打断递推 */
    private fun emaSeq(x: List<Double?>, n: Int): List<Double?> {
        val out = arrayOfNulls<Double>(x.size)
        val k = 2.0 / (n + 1)
        var prev: Double? = null
        for (i in x.indices) {
            val v = x[i] ?: continue
            prev = if (prev == null) v else v * k + prev * (1 - k)
            out[i] = clean(prev)
        }
        return out.toList()
    }

    /** 通达信 SMA: y = (x*m + y'*(n-m))/n，与 Indicators.smaTdx 一致 */
    private fun smaSeq(x: List<Double?>, n: Int, m: Int): List<Double?> {
        val out = arrayOfNulls<Double>(x.size)
        var prev: Double? = null
        for (i in x.indices) {
            val v = x[i] ?: continue
            prev = if (prev == null) v else (v * m + prev * (n - m)) / n
            out[i] = clean(prev)
        }
        return out.toList()
    }

    private fun refSeq(x: List<Double?>, n: Int): List<Double?> =
        x.indices.map { i -> if (i - n >= 0) x[i - n] else null }

    /** 上穿: 前一根 a<=b 且当前 a>b -> 1, 否则 0; 当前值缺失 -> null */
    private fun crossSeq(a: List<Double?>, b: List<Double?>): List<Double?> =
        a.indices.map { i ->
            val x = a[i]; val y = b[i]
            if (x == null || y == null) return@map null
            if (i == 0) return@map 0.0
            val px = a[i - 1]; val py = b[i - 1]
            if (px != null && py != null && px <= py && x > y) 1.0 else 0.0
        }

    private fun ifSeq(cond: List<Double?>, a: List<Double?>, b: List<Double?>): List<Double?> =
        cond.indices.map { i ->
            val c = cond[i] ?: return@map null
            if (c != 0.0) a[i] else b[i]
        }

    /** 最近 n 根中条件成立(非0非null)的根数; 窗口不足 -> null */
    private fun countSeq(cond: List<Double?>, n: Int): List<Double?> {
        val out = arrayOfNulls<Double>(cond.size)
        for (i in cond.indices) {
            if (i < n - 1) continue
            var cnt = 0
            for (j in i - n + 1..i) {
                val v = cond[j]
                if (v != null && v != 0.0) cnt++
            }
            out[i] = cnt.toDouble()
        }
        return out.toList()
    }

    // ---------- validate 用的合成数据 ----------

    private fun syntheticKlines(): List<Kline> = (0 until 30).map { i ->
        val base = 10.0 + 2.0 * sin(i / 3.0) + i * 0.1
        Kline(
            time = i * 86_400_000L,
            label = "D$i",
            open = base - 0.2,
            high = base + 0.5,
            low = base - 0.5,
            close = base,
            volume = 10000.0 + i * 100,
            amount = (10000.0 + i * 100) * base
        )
    }
}
