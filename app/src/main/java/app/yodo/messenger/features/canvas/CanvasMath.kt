package app.yodo.messenger.features.canvas

/** Deliberately bounded school-math subset, not a TeX interpreter. No macros, files or network. */
sealed interface MathNode {
    data class Row(val children:List<MathNode>):MathNode
    data class Glyph(val text:String):MathNode
    data class Gap(val em:Float):MathNode
    data class Fraction(val numerator:MathNode,val denominator:MathNode):MathNode
    data class Root(val body:MathNode,val index:MathNode?):MathNode
    data class Scripts(val base:MathNode,val upper:MathNode?,val lower:MathNode?):MathNode
    data class Accent(val body:MathNode,val kind:String):MathNode
    data class Fenced(val body:MathNode,val left:String,val right:String):MathNode
}
object CanvasMath {
    const val MAX_SOURCE=256
    const val MAX_FORMULAS=30
    val examples=linkedMapOf(
        "Дробь" to "\\frac{a+b}{c}","Корень" to "\\sqrt{x^2+y^2}",
        "Квадратное уравнение" to "x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}",
        "Тригонометрия" to "\\sin^2 x+\\cos^2 x=1",
        "Сумма" to "\\sum_{i=1}^{n} i=\\frac{n(n+1)}{2}",
        "Интеграл" to "\\int_0^1 x^2 dx=\\frac{1}{3}"
    )
    private val symbols=mapOf("pi" to "π","alpha" to "α","beta" to "β","gamma" to "γ","delta" to "δ","theta" to "θ","lambda" to "λ","mu" to "μ","sigma" to "σ","phi" to "φ","omega" to "ω","Delta" to "Δ","Sigma" to "Σ","Omega" to "Ω",
        "pm" to "±","mp" to "∓","times" to "×","cdot" to "·","div" to "÷","leq" to "≤","geq" to "≥","neq" to "≠","approx" to "≈","infty" to "∞","sum" to "∑","prod" to "∏","int" to "∫","oint" to "∮","partial" to "∂","nabla" to "∇",
        "rightarrow" to "→","leftarrow" to "←","leftrightarrow" to "↔","Rightarrow" to "⇒","Leftrightarrow" to "⇔","angle" to "∠","triangle" to "△","perp" to "⊥","parallel" to "∥","in" to "∈","notin" to "∉","subset" to "⊂","subseteq" to "⊆","cup" to "∪","cap" to "∩","emptyset" to "∅","forall" to "∀","exists" to "∃","ldots" to "…","cdots" to "⋯")
    private val functions=setOf("sin","cos","tan","cot","log","ln","exp","lim","min","max","arcsin","arccos","arctan")
    fun normalize(raw:String):String {
        require(raw.length<=MAX_SOURCE+4){"Формула: не более 256 символов"}
        var s=raw.trim()
        if(s.startsWith("$$")&&s.endsWith("$$")&&s.length>=4)s=s.substring(2,s.length-2).trim()
        else if(s.startsWith('$')&&s.endsWith('$')&&s.length>=2)s=s.substring(1,s.length-1).trim()
        require(s.isNotBlank()&&s.length<=MAX_SOURCE){"Введите формулу (до 256 символов)"}
        return s.replace('\n',' ').replace('\r',' ').replace('\t',' ')
    }
    fun parse(raw:String):MathNode = Parser(normalize(raw)).parse()
    private class Parser(val source:String){
        var pos=0;var depth=0;var nodes=0;var commands=0
        fun node(n:MathNode):MathNode {require(++nodes<=160){"Слишком сложная формула"};return n}
        fun rightAhead()=source.startsWith("\\right",pos)&&(pos+6>=source.length||!source[pos+6].isLetter())
        fun skip(){while(pos<source.length&&source[pos].isWhitespace())pos++}
        fun parse():MathNode {val value=row(null,false);skip();require(pos==source.length){"Лишняя скобка"};require(ink(value)){"Формула пуста"};return value}
        fun ink(n:MathNode):Boolean=when(n){is MathNode.Gap->false;is MathNode.Row->n.children.any(::ink);is MathNode.Fenced->n.left!="."||n.right!="."||ink(n.body);is MathNode.Scripts->ink(n.base)||n.upper?.let(::ink)==true||n.lower?.let(::ink)==true;else->true}
        fun row(end:Char?,right:Boolean):MathNode {
            require(++depth<=12){"Слишком глубокая вложенность"}
            try {
                val children=mutableListOf<MathNode>()
                while(true){skip();if(pos>=source.length||end!=null&&source[pos]==end||right&&rightAhead())break
                    var base=atom();var upper:MathNode?=null;var lower:MathNode?=null
                    while(true){skip();if(pos>=source.length||source[pos]!='^'&&source[pos]!='_')break
                        val marker=source[pos++];val part=argument(false)
                        if(marker=='^'){require(upper==null){"Повторный верхний индекс: используйте скобки"};upper=part}
                        else{require(lower==null){"Повторный нижний индекс: используйте скобки"};lower=part}
                    }
                    if(upper!=null||lower!=null)base=node(MathNode.Scripts(base,upper,lower))
                    children.add(base)
                }
                require(children.isNotEmpty()){ "Пустая группа или пропущен аргумент" }
                return node(MathNode.Row(children))
            } finally {depth--}
        }
        fun argument(braces:Boolean):MathNode {skip();require(pos<source.length){"Пропущен аргумент"}
            if(source[pos]=='{'){pos++;val value=row('}',false);require(pos<source.length&&source[pos++]=='}'){"Не закрыта фигурная скобка"};return value}
            require(!braces){"Аргумент команды нужно заключить в { }"};return atom()
        }
        fun delimiter():String {skip();require(pos<source.length){"Пропущена скобка"}
            val c=source[pos++];if(c=='\\'){require(pos<source.length&&source[pos] in "{}"){"Поддерживаются скобки (), [], {}, | и ."};return source[pos++].toString()}
            require(c in "()[]|.<>"){"Поддерживаются скобки (), [], {}, | и ."};return c.toString()
        }
        fun atom():MathNode {skip();require(pos<source.length){"Пропущен аргумент"};val c=source[pos]
            if(c=='{')return argument(true)
            require(c!='}'&&c!='^'&&c!='_'){"Лишняя скобка или индекс без основания"}
            if(c=='\\'){
                pos++;require(++commands<=48){"Слишком много команд"};require(pos<source.length){"Незавершённая команда"}
                if(source[pos] in ",;! "){val spacing=source[pos++];return node(MathNode.Gap(if(spacing==',')0.16f else if(spacing=='!')0.06f else 0.3f))}
                val start=pos;while(pos<source.length&&source[pos] in 'a'..'z'||pos<source.length&&source[pos] in 'A'..'Z')pos++
                require(pos>start){"Неизвестная команда"};val command=source.substring(start,pos)
                symbols[command]?.let{return node(MathNode.Glyph(it))}
                if(command in functions)return node(MathNode.Glyph(command))
                return when(command){
                    "frac"->node(MathNode.Fraction(argument(true),argument(true)))
                    "sqrt"->{skip();val index=if(pos<source.length&&source[pos]=='['){pos++;val value=row(']',false);require(pos<source.length&&source[pos++]==']'){"Не закрыт индекс корня"};value}else null;node(MathNode.Root(argument(true),index))}
                    "overline","bar","vec","hat"->node(MathNode.Accent(argument(true),command))
                    "mathrm"->argument(true)
                    "left"->{val left=delimiter();val body=row(null,true);require(rightAhead()){"Для \\left нужна \\right"};pos+=6;node(MathNode.Fenced(body,left,delimiter()))}
                    else->throw IllegalArgumentException("Команда \\$command не поддерживается")
                }
            }
            require(c in 'a'..'z'||c in 'A'..'Z'||c in '0'..'9'||c in "+-*/=.,:;!<>|()[]"||symbols.values.any{it==c.toString()}){"Используйте латинские переменные и команды LaTeX; обычный текст — инструментом «Текст»"}
            pos++;return node(MathNode.Glyph(c.toString()))
        }
    }
}
