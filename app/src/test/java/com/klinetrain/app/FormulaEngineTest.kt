package com.klinetrain.app

import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.formula.FormulaEngine
import com.klinetrain.app.formula.FormulaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * FormulaEngine 单元测试。
 * 数据：20 根手工K线，close = 1..20, high = close+1, low = close-1, open = close-0.5。
 */
class FormulaEngineTest {

    private val klines: List<Kline> = (0 until 20).map { i ->
        val close = (i + 1).toDouble()
        Kline(
            time = i * 86_400_000L,
            label = "D$i",
            open = close - 0.5,
            high = close + 1.0,
            low = close - 1.0,
            close = close,
            volume = 100.0 * (i + 1),
            amount = 1000.0 * (i + 1)
        )
    }

    private val delta = 1e-6

    // 1. MA
    @Test
    fun testMa() {
        val out = FormulaEngine.evaluate("M: MA(C,5)", klines)
        val m = out["M"]!!
        assertEquals(20, m.size)
        assertNull(m[0])
        assertNull(m[3])                       // 暖机期
        assertEquals(3.0, m[4]!!, delta)       // (1+2+3+4+5)/5
        assertEquals(18.0, m[19]!!, delta)     // (16+..+20)/5
    }

    // 2. REF
    @Test
    fun testRef() {
        val out = FormulaEngine.evaluate("R: REF(C,3);", klines)
        val r = out["R"]!!
        assertNull(r[0])
        assertNull(r[2])
        assertEquals(1.0, r[3]!!, delta)
        assertEquals(17.0, r[19]!!, delta)
    }

    // 3. CROSS (标量广播: C 上穿 10.5 发生在 close 10 -> 11 处)
    @Test
    fun testCross() {
        val out = FormulaEngine.evaluate("X: CROSS(C, 10.5)", klines)
        val x = out["X"]!!
        assertEquals(0.0, x[0]!!, delta)
        assertEquals(0.0, x[9]!!, delta)       // close=10 未上穿
        assertEquals(1.0, x[10]!!, delta)      // 10 <= 10.5 且 11 > 10.5
        assertEquals(0.0, x[11]!!, delta)      // 已在上方，不再触发
    }

    // 4. IF + 比较
    @Test
    fun testIf() {
        val out = FormulaEngine.evaluate("Y: IF(C>=5, C, -C)", klines)
        val y = out["Y"]!!
        assertEquals(-4.0, y[3]!!, delta)      // close=4, 条件不成立
        assertEquals(5.0, y[4]!!, delta)       // close=5, 条件成立
        assertEquals(20.0, y[19]!!, delta)
    }

    // 5. HHV / LLV
    @Test
    fun testHhvLlv() {
        val out = FormulaEngine.evaluate("HH: HHV(H,3);\nLL: LLV(L,3);", klines)
        val hh = out["HH"]!!
        val ll = out["LL"]!!
        assertNull(hh[1])
        assertEquals(6.0, hh[4]!!, delta)      // high 4,5,6 中最高
        assertEquals(21.0, hh[19]!!, delta)    // high 19,20,21
        assertEquals(2.0, ll[4]!!, delta)      // low 2,3,4 中最低
        assertEquals(17.0, ll[19]!!, delta)
    }

    // 6. 算术运算 + 优先级 + 一元负号
    @Test
    fun testArithmetic() {
        val out = FormulaEngine.evaluate("A: (C*2+1)/3;\nB: -C+2*3;", klines)
        assertEquals(13.0 / 3.0, out["A"]!![5]!!, delta)   // close=6
        assertEquals(1.0, out["A"]!![0]!!, delta)          // (1*2+1)/3
        assertEquals(5.0, out["B"]!![0]!!, delta)          // -1 + 6
        assertEquals(-14.0, out["B"]!![19]!!, delta)       // -20 + 6
    }

    // 7. 比较 + AND/OR 逻辑
    @Test
    fun testComparisonAndLogic() {
        val out = FormulaEngine.evaluate("B: C>5 AND C<10", klines)
        val b = out["B"]!!
        assertEquals(0.0, b[3]!!, delta)       // 4: 不满足
        assertEquals(1.0, b[5]!!, delta)       // 6: 满足
        assertEquals(0.0, b[9]!!, delta)       // 10: C<10 不满足
        val out2 = FormulaEngine.evaluate("B: C<=2 || C>=19", klines)
        assertEquals(1.0, out2["B"]!![0]!!, delta)
        assertEquals(0.0, out2["B"]!![10]!!, delta)
        assertEquals(1.0, out2["B"]!![19]!!, delta)
    }

    // 8. 中间变量 := 不输出
    @Test
    fun testIntermediateVariable() {
        val out = FormulaEngine.evaluate("X := MA(C,5);\nD: C - X;", klines)
        assertFalse("中间变量不应输出", out.containsKey("X"))
        assertTrue(out.containsKey("D"))
        val d = out["D"]!!
        assertNull(d[3])                        // X 暖机期 -> null 传播
        assertEquals(2.0, d[4]!!, delta)        // 5 - 3
        assertEquals(2.0, d[19]!!, delta)       // 20 - 18
    }

    // 9. EMA 精确值 (k = 2/(3+1) = 0.5)
    @Test
    fun testEma() {
        val out = FormulaEngine.evaluate("E: EMA(C,3)", klines)
        val e = out["E"]!!
        assertEquals(1.0, e[0]!!, delta)
        assertEquals(1.5, e[1]!!, delta)        // 2*0.5 + 1*0.5
        assertEquals(2.25, e[2]!!, delta)       // 3*0.5 + 1.5*0.5
        assertEquals(3.125, e[3]!!, delta)      // 4*0.5 + 2.25*0.5
    }

    // 10. SUM + COUNT
    @Test
    fun testSumAndCount() {
        val out = FormulaEngine.evaluate("S: SUM(C,3);\nN: COUNT(C>5, 4);", klines)
        val s = out["S"]!!
        val n = out["N"]!!
        assertNull(s[1])
        assertEquals(6.0, s[2]!!, delta)        // 1+2+3
        assertEquals(57.0, s[19]!!, delta)      // 18+19+20
        assertNull(n[2])                         // 窗口不足
        assertEquals(3.0, n[7]!!, delta)        // 5,6,7,8 中 >5 的有 3 个
        assertEquals(4.0, n[19]!!, delta)
    }

    // 11. 除以 0 -> null
    @Test
    fun testDivisionByZeroIsNull() {
        val out = FormulaEngine.evaluate("Z: C/(C-C)", klines)
        val z = out["Z"]!!
        assertEquals(20, z.size)
        assertNull(z[0])
        assertNull(z[19])
    }

    // 12. 未知函数报错(带行号)
    @Test
    fun testUnknownFunctionError() {
        try {
            FormulaEngine.evaluate("A: XYZ(C,5)", klines)
            fail("应抛出 FormulaException")
        } catch (e: FormulaException) {
            assertTrue(e.message!!.contains("未知函数"))
            assertTrue(e.message!!.contains("XYZ"))
            assertTrue(e.message!!.contains("第1行"))
        }
        val msg = FormulaEngine.validate("A: MA(C,5);\nB: FOO(C,3);")
        assertNotNull(msg)
        assertTrue(msg!!.contains("第2行"))
        assertTrue(msg.contains("未知函数"))
    }

    // 13. 语法错误 / 未知变量 / 空公式
    @Test
    fun testInvalidFormulas() {
        assertNotNull("括号不闭合应报错", FormulaEngine.validate("A: (C+"))
        assertNotNull("缺少冒号应报错", FormulaEngine.validate("MA(C,5)"))
        assertNotNull("未知变量应报错", FormulaEngine.validate("A: FOO + 1"))
        assertNotNull("空公式应报错", FormulaEngine.validate("   "))
        assertNotNull("只有中间变量应报错", FormulaEngine.validate("X := MA(C,5);"))
        assertNotNull("周期参数为序列应报错", FormulaEngine.validate("A: MA(C, C)"))
        // 合法公式 validate 返回 null
        assertNull(FormulaEngine.validate("MA5: MA(C,5); MA10: MA(C,10);"))
        assertNull(FormulaEngine.validate("// 注释\nDIFF: EMA(C,12)-EMA(C,26);"))
    }

    // 14. 多变量别名 + 一行多语句 + 注释
    @Test
    fun testAliasesAndComments() {
        val out = FormulaEngine.evaluate(
            "A: OPEN; B: HIGH; // 注释部分 C: LOW\nD: V/100;",
            klines
        )
        assertEquals(3, out.size)               // 注释掉的 C 不存在
        assertEquals(0.5, out["A"]!![0]!!, delta)   // open = 1 - 0.5
        assertEquals(2.0, out["B"]!![0]!!, delta)   // high = 1 + 1
        assertEquals(1.0, out["D"]!![0]!!, delta)   // 100/100
        assertEquals(20.0, out["D"]!![19]!!, delta)
    }

    // 15. MAX/MIN/ABS 组合
    @Test
    fun testMaxMinAbs() {
        val out = FormulaEngine.evaluate("M: MAX(C-10, 0);\nN: MIN(C, 5);\nA: ABS(C-10);", klines)
        assertEquals(0.0, out["M"]!![3]!!, delta)   // max(4-10, 0)
        assertEquals(10.0, out["M"]!![19]!!, delta)
        assertEquals(4.0, out["N"]!![3]!!, delta)
        assertEquals(5.0, out["N"]!![19]!!, delta)
        assertEquals(6.0, out["A"]!![3]!!, delta)   // |4-10|
        assertEquals(0.0, out["A"]!![9]!!, delta)   // |10-10|
    }
}
