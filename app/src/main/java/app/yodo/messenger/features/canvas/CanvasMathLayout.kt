package app.yodo.messenger.features.canvas

import kotlin.math.*

data class MathMetrics(val width:Float,val ascent:Float,val descent:Float)
fun interface MathMeasure {fun measure(text:String,size:Float):MathMetrics}
sealed interface MathInk {
    data class Text(val text:String,val x:Float,val baseline:Float,val size:Float):MathInk
    data class Line(val x1:Float,val y1:Float,val x2:Float,val y2:Float,val width:Float):MathInk
    data class Curve(val x1:Float,val y1:Float,val cx:Float,val cy:Float,val x2:Float,val y2:Float,val width:Float):MathInk
}
data class MathLayout(val width:Float,val ascent:Float,val descent:Float,val ink:List<MathInk>){val height get()=ascent+descent}
object CanvasMathLayout {
    private fun move(ink:MathInk,x:Float,y:Float):MathInk=when(ink){
        is MathInk.Text->ink.copy(x=ink.x+x,baseline=ink.baseline+y)
        is MathInk.Line->ink.copy(x1=ink.x1+x,y1=ink.y1+y,x2=ink.x2+x,y2=ink.y2+y)
        is MathInk.Curve->ink.copy(x1=ink.x1+x,y1=ink.y1+y,cx=ink.cx+x,cy=ink.cy+y,x2=ink.x2+x,y2=ink.y2+y)
    }
    fun layout(node:MathNode,size:Float,measure:MathMeasure):MathLayout {
        require(size.isFinite()&&size in 4f..96f)
        fun at(n:MathNode,s:Float)=layout(n,s.coerceAtLeast(4f),measure)
        fun shifted(l:MathLayout,x:Float,y:Float)=l.ink.map{move(it,x,y)}
        val result=when(node){
            is MathNode.Glyph->{val m=measure.measure(node.text,size);val pad=if(node.text in listOf("+","-","=","±","≤","≥","≠","≈","×","→","⇒"))size*.1f else 0f
                MathLayout(m.width+pad*2,m.ascent,m.descent,listOf(MathInk.Text(node.text,pad,0f,size)))}
            is MathNode.Gap->MathLayout(node.em*size,0f,0f,emptyList())
            is MathNode.Row->{val list=node.children.map{at(it,size)};var x=0f;val ink=mutableListOf<MathInk>()
                list.forEach{ink+=shifted(it,x,0f);x+=it.width};MathLayout(x,list.maxOf{it.ascent},list.maxOf{it.descent},ink)}
            is MathNode.Fraction->{val n=at(node.numerator,size*.86f);val d=at(node.denominator,size*.86f);val pad=size*.16f;val width=max(n.width,d.width)+pad*2
                val axis=-size*.25f;val top=axis-size*.16f-n.descent;val bottom=axis+size*.16f+d.ascent
                MathLayout(width,max(0f,n.ascent-top),max(0f,bottom+d.descent),shifted(n,(width-n.width)/2,top)+shifted(d,(width-d.width)/2,bottom)+MathInk.Line(pad*.3f,axis,width-pad*.3f,axis,max(1f,size*.045f)))}
            is MathNode.Root->{val b=at(node.body,size);val index=node.index?.let{at(it,size*.5f)};val inset=max(0f,(index?.width?:0f)-size*.3f);val x=inset+size*.65f;val top=-b.ascent-size*.18f
                val ink=shifted(b,x,0f).toMutableList();val width=x+b.width+size*.1f;val stroke=max(1f,size*.045f)
                ink+=MathInk.Line(inset,-size*.15f,inset+size*.15f,-size*.27f,stroke);ink+=MathInk.Line(inset+size*.15f,-size*.27f,inset+size*.3f,size*.04f,stroke)
                ink+=MathInk.Line(inset+size*.3f,size*.04f,inset+size*.5f,top,stroke);ink+=MathInk.Line(inset+size*.5f,top,width,top,stroke)
                if(index!=null)ink+=shifted(index,0f,-size*.5f)
                MathLayout(width,maxOf(-top+stroke,size*.3f,(index?.ascent?:0f)+if(index!=null)size*.5f else 0f),maxOf(b.descent,size*.08f,(index?.descent?:0f)-if(index!=null)size*.5f else 0f),ink)}
            is MathNode.Scripts->{val b=at(node.base,size);val u=node.upper?.let{at(it,size*.65f)};val d=node.lower?.let{at(it,size*.65f)}
                val uy=if(u==null)0f else min(-size*.65f,-b.ascent*.7f-u.descent)
                var dy=if(d==null)0f else max(size*.3f,b.descent+d.ascent*.45f)
                if(u!=null&&d!=null)dy=max(dy,uy+u.descent+size*.12f+d.ascent)
                val ink=b.ink.toMutableList();val x=b.width+size*.05f;if(u!=null)ink+=shifted(u,x,uy);if(d!=null)ink+=shifted(d,x,dy)
                MathLayout(x+max(u?.width?:0f,d?.width?:0f),max(b.ascent,(u?.ascent?:0f)-uy),max(b.descent,(d?.descent?:0f)+dy),ink)}
            is MathNode.Accent->{val b=at(node.body,size);val y=-b.ascent-size*.16f;val stroke=max(1f,size*.045f);val span=max(b.width,size*.25f);val ink=shifted(b,(span-b.width)/2,0f).toMutableList()
                if(node.kind=="hat"){ink+=MathInk.Line(0f,y,span/2,y-size*.14f,stroke);ink+=MathInk.Line(span/2,y-size*.14f,span,y,stroke)}
                else{ink+=MathInk.Line(0f,y,span,y,stroke);if(node.kind=="vec"){ink+=MathInk.Line(span,y,span-size*.14f,y-size*.1f,stroke);ink+=MathInk.Line(span,y,span-size*.14f,y+size*.1f,stroke)}}
                MathLayout(span,b.ascent+size*.35f,b.descent,ink)}
            is MathNode.Fenced->{val b=at(node.body,size);val dw=size*.25f;val gap=size*.12f;val left=if(node.left==".")0f else dw+gap;val right=if(node.right==".")0f else dw+gap
                val top=-b.ascent-size*.1f;val bottom=b.descent+size*.1f;val ink=shifted(b,left,0f).toMutableList();val stroke=max(1f,size*.045f)
                fun fence(symbol:String,x:Float){val middle=(top+bottom)/2
                    fun line(a:Float,c:Float,d:Float,e:Float){ink+=MathInk.Line(x+a,c,x+d,e,stroke)}
                    when(symbol){
                        "."->Unit
                        "("->{ink+=MathInk.Curve(x+dw,top,x,top+(bottom-top)*.25f,x+dw*.2f,middle,stroke);ink+=MathInk.Curve(x+dw*.2f,middle,x,bottom-(bottom-top)*.25f,x+dw,bottom,stroke)}
                        ")"->{ink+=MathInk.Curve(x,top,x+dw,top+(bottom-top)*.25f,x+dw*.8f,middle,stroke);ink+=MathInk.Curve(x+dw*.8f,middle,x+dw,bottom-(bottom-top)*.25f,x,bottom,stroke)}
                        "["->{line(dw,top,0f,top);line(0f,top,0f,bottom);line(0f,bottom,dw,bottom)}
                        "]"->{line(0f,top,dw,top);line(dw,top,dw,bottom);line(dw,bottom,0f,bottom)}
                        "|"->line(dw/2,top,dw/2,bottom)
                        "<"->{line(dw,top,0f,middle);line(0f,middle,dw,bottom)}
                        ">"->{line(0f,top,dw,middle);line(dw,middle,0f,bottom)}
                        "{"->{ink+=MathInk.Curve(x+dw,top,x+dw*.35f,top,x+dw*.35f,(top+middle)/2,stroke);ink+=MathInk.Curve(x+dw*.35f,(top+middle)/2,x+dw*.35f,middle,x,middle,stroke);ink+=MathInk.Curve(x,middle,x+dw*.35f,middle,x+dw*.35f,(middle+bottom)/2,stroke);ink+=MathInk.Curve(x+dw*.35f,(middle+bottom)/2,x+dw*.35f,bottom,x+dw,bottom,stroke)}
                        "}"->{ink+=MathInk.Curve(x,top,x+dw*.65f,top,x+dw*.65f,(top+middle)/2,stroke);ink+=MathInk.Curve(x+dw*.65f,(top+middle)/2,x+dw*.65f,middle,x+dw,middle,stroke);ink+=MathInk.Curve(x+dw,middle,x+dw*.65f,middle,x+dw*.65f,(middle+bottom)/2,stroke);ink+=MathInk.Curve(x+dw*.65f,(middle+bottom)/2,x+dw*.65f,bottom,x,bottom,stroke)}
                    }
                }
                fence(node.left,0f);fence(node.right,left+b.width+gap)
                MathLayout(left+b.width+right,-top,bottom,ink)}
        }
        require(result.width.isFinite()&&result.ascent.isFinite()&&result.descent.isFinite()&&result.width>=0&&result.ascent>=0&&result.descent>=0&&result.ink.size<=1024){"Слишком сложная формула"}
        return result
    }
    fun bounds(anchor:CanvasPoint,layout:MathLayout,doc:CanvasDocument):List<CanvasPoint>{
        require(layout.width>0&&layout.height>0)
        val scale=minOf(1f,(doc.width-24)/layout.width,(doc.height-24)/layout.height)
        require(scale>=.25f){"Формула слишком большая. Сократите её или уменьшите шрифт."}
        val w=layout.width*scale;val h=layout.height*scale
        val x=anchor.x.coerceIn(12f,(doc.width-w-12f).coerceAtLeast(12f));val y=anchor.y.coerceIn(12f,(doc.height-h-12f).coerceAtLeast(12f))
        return listOf(CanvasPoint(x,y),CanvasPoint(x+w,y+h))
    }
}
