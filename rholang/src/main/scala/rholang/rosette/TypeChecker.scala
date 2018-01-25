// -*- mode: Scala;-*- 
// Filename:    TypeChecker.scala 
// Authors:     Kyle Butt, Eitan Chatav                                                  
// Creation:    Thurs Dec 7 11:43:00 2017 
// Copyright:   See site license 
// Description: Spatial type checker; see Namespace Logic paper by LGM.
// ------------------------------------------------------------------------

package coop.rchain.rho2rose

// import coop.rchain.syntax.rholang._
import coop.rchain.syntax.rholang.Absyn._
import scalaz.{Bind => _, _}
import scalaz.std.list._
import scalaz.std.option._

object CheckerTypes {
    type TypeError = String
    type Arg = Option[TPattern]
    type Ret = List[TypeError]
}

class TypeCheckVisitor
extends Contr.Visitor[CheckerTypes.Ret, CheckerTypes.Arg]
with Proc.Visitor[CheckerTypes.Ret, CheckerTypes.Arg]{
    import CheckerTypes._
    override def visit( p: DContr, arg: Arg ): Ret = {
        println("typechecker ran on" + p)
        List()
    }
    override def visit( p: PNil, arg: Arg ): Ret = List()
    override def visit( p: PValue, arg: Arg ): Ret = List()
    override def visit( p: PDrop, arg: Arg ): Ret = List()
    override def visit( p: PLift, arg: Arg ): Ret = List()
    override def visit( p: PInput, arg: Arg ): Ret = List()
    override def visit( p: PChoice, arg: Arg ): Ret = List()
    override def visit( p: PMatch, arg: Arg ): Ret = List()
    override def visit( p: PNew, arg: Arg ): Ret = List()
    override def visit( p: PPrint, arg: Arg ): Ret = List()
    override def visit( p: PConstr, arg: Arg ): Ret = List()
    override def visit( p: PContr, arg: Arg ): Ret = List()
    override def visit( p: PPar, arg: Arg ): Ret = List()
}

class SatisfiedVisitor
extends TPattern.Visitor[Boolean, Proc]{
    override def visit( tp: TPVerity, arg: Proc ): Boolean = true
    override def visit( tp: TPNegation, arg: Proc ): Boolean = {
        ! (tp.tpattern_.accept(this, arg))
    }
    override def visit ( tp: TPNullity, arg: Proc): Boolean = {
      structurallyEquivalent(arg, new PNil())
    }
    override def visit ( tp: TPConjuction, arg: Proc): Boolean = {
      (tp.tpattern_1.accept(this, arg) && tp.tpattern_2.accept(this, arg))
    }
    override def visit ( tp: TPDisjunction, arg: Proc): Boolean = {
      (tp.tpattern_1.accept(this, arg) || tp.tpattern_2.accept(this, arg))
    }
    override def visit ( tp: TPDescent, arg: Proc): Boolean = {
      arg match {
        case pdrop: PDrop => nameEquivalent(pdrop.chan_, tp.chan_)
        case _: Any => false
      }
    }
    override def visit ( tp: TPElevation, arg: Proc): Boolean = {
      arg match {
        case plift : PLift => {
          if (plift.listproc_.size() == 1) {
            nominallySafisfies( tp.tpindicator_, plift.chan_ ) &&
            tp.tpattern_.accept(this, plift.listproc_.get(0))
          } else {
            sys.error("unimplemented")
          }
        }
        case _: Any => false
      }
    }
    override def visit ( tp: TPActivity, arg: Proc): Boolean = sys.error("unimplemented")
    override def visit ( tp: TPMixture, arg: Proc): Boolean = sys.error("unimplemented")
    
    def structurallyEquivalent( p1: Proc, p2: Proc): Boolean = sys.error("unimplemented")
    def nameEquivalent( p1: Chan, p2: Chan): Boolean = sys.error("unimplemented")
    def nominallySafisfies( ind: TPIndicator, chan: Chan): Boolean = {
      chan.accept(new NominallySatisfiedVisitor, ind)
    }
}

class NominallySatisfiedVisitor
extends Chan.Visitor[Boolean,TPIndicator]{
  override def visit ( cquote: CQuote, arg: TPIndicator): Boolean = {
    val proc = cquote.proc_
    arg match {
      case tpiquote: TPIQuotFormula =>
        tpiquote.tpattern_.accept(new SatisfiedVisitor, proc)
      case tpichan: TPIChan =>
        sys.error("unimplemented")
    }
  }
  override def visit ( cvar: CVar, arg: TPIndicator): Boolean = {
    sys.error("unimplemented")
  }
}

object Equivalences{

  def nameEquivalent(env1: DeBruijn, n1: Chan, env2: DeBruijn, n2: Chan): Boolean = {
    (n1, n2) match {
      case (q1: CQuote, q2: CQuote) =>
        q1.proc_ match {
          case pdrop1: PDrop => nameEquivalent(pdrop1.chan_, n2)
          case _ => q2.proc_ match {
            case pdrop2: PDrop => nameEquivalent(n1, pdrop2.chan_)
            case _ => structurallyEquivalent(q1.proc_, q2.proc_)
          }
        }
      case (q1: CVar, q2: CVar) => env1.equivalent(q1.var_, env2, q2.var_)
      case _ => false
    }
  }

  def nameEquivalent(n1: Chan, n2: Chan): Boolean =
    nameEquivalent(DeBruijn(), n1, DeBruijn(), n2)

  def structurallyNil(p: Proc): Boolean = {
    p match {
      case _ : PNil => true
      case ppar : PPar =>
        structurallyNil(ppar.proc_1) && structurallyNil (ppar.proc_2)
      case _ => false
    }
  }

  def structurallyEquivalent(env1: DeBruijn, p1: Proc, env2: DeBruijn, p2: Proc): Boolean = {
    (p1,p2) match {
      case (_ : PNil, _) => structurallyNil(p2)
      case (_, _ : PNil) => structurallyNil(p1)
      case (_: PValue, _: PValue) => ???
      case (drop1: PDrop, drop2: PDrop) =>
        nameEquivalent(env1, drop1.chan_, env2, drop2.chan_)
      case (lift1: PLift, lift2: PLift) => {
        nameEquivalent(env1, lift1.chan_, env2, lift2.chan_) &&
          allStructurallyEquivalent(env1, lift1.listproc_, env2, lift2.listproc_)
      }
      case (input1: PInput, input2: PInput) => ???
      case (_: PChoice, _: PChoice) => ???
      case (_: PMatch, _: PMatch) => ???
      case (_: PNew, _: PNew) => ???
      case (print1: PPrint, print2: PPrint) =>
        structurallyEquivalent(env1, print1.proc_, env2, print2.proc_)
      case (constr1: PConstr, constr2: PConstr) => {
        env1.equivalent(constr1.var_, env2, constr2.var_) &&
          allStructurallyEquivalent(env1, constr1.listproc_, env2, constr2.listproc_)
      }
      case (contr1: PContr, contr2: PContr) => {
        allCPatternEquivalent(env1, contr1.listcpattern_, env2, contr2.listcpattern_) match {
          case None => false
          case Some((newenv1,newenv2)) =>
            env1.equivalent(contr1.var_, env2, contr2.var_) &&
              structurallyEquivalent(newenv1, contr1.proc_, newenv2, contr2.proc_)
        }
      }
      case (_: PPar, _: PPar) => ???
      case _ => false
    }
  }

  def bindEquivalent(env1: DeBruijn, b1: Bind, env2: DeBruijn, b2: Bind): Option[(CPattern,CPattern)] = {
    (b1, b2) match {
      case (inpBind1: InputBind, inpBind2: InputBind) =>
        if (nameEquivalent(env1, inpBind1.chan_, env2, inpBind2.chan_)) {
          Some((inpBind1.cpattern_, inpBind2.cpattern_))
        } else None
      case (condInpBind1: CondInputBind, condInpBind2: CondInputBind) =>
        val check =
          nameEquivalent(env1, condInpBind1.chan_, env2, condInpBind2.chan_) &&
          structurallyEquivalent(env1, condInpBind1.proc_, env2, condInpBind2.proc_)
        if (check) {
          Some((condInpBind1.cpattern_, condInpBind2.cpattern_))
        } else None
      case _ => None 
    }
  }

  def syntacticSubstitution(proc: Proc, source: CPattern, target: CPattern): Proc = ???

  def alphaEquivalent(p1: Proc, p2: Proc): Boolean = ???

  def structurallyEquivalent(p1: Proc, p2: Proc): Boolean =
    structurallyEquivalent(DeBruijn(), p1, DeBruijn(), p2)
  
  def allStructurallyEquivalent(env1: DeBruijn, ps1: ListProc, env2: DeBruijn, ps2: ListProc): Boolean = {
    import scala.collection.JavaConverters._
    ps1.size() == ps2.size() &&
      (ps1.asScala.toList, ps2.asScala.toList).zipped.forall(
        (proc1,proc2) => structurallyEquivalent(env1, proc2, env2, proc2)
        )
  }

  def cpatternEquivalent(env1: DeBruijn, cp1: CPattern, env2: DeBruijn, cp2: CPattern): Option[(DeBruijn, DeBruijn)] = {
    (cp1, cp2) match {
      case (cpvar1: CPtVar, cpvar2: CPtVar) =>
        (cpvar1.varpattern_, cpvar2.varpattern_) match {
          case (_: VarPtWild, _: VarPtWild) => Some((env1,env2))
          case (v1: VarPtVar, v2: VarPtVar) => {
            Some((env1.newBindings(List(v1.var_)), env2.newBindings(List(v2.var_))))
          }
          case _ => None
        }
      case (cpval1: CValPtrn, cpval2: CValPtrn) => sys.error("unimplemented")
      case (cpq1: CPtQuote, cpq2: CPtQuote) => sys.error("unimplemented")
      case _ => None
    }
  }

  def allCPatternEquivalent(env1: DeBruijn, cps1: ListCPattern, env2: DeBruijn, cps2: ListCPattern): Option[(DeBruijn, DeBruijn)] = {
    import scala.collection.JavaConverters._
    if (cps1.size() != cps2.size()) {
      None
    } else {
      val list = cps1.asScala.toList.zip(cps2.asScala.toList)
      def step(envs: (DeBruijn,DeBruijn), cps: (CPattern,CPattern)): Option[(DeBruijn,DeBruijn)] =
        cpatternEquivalent(envs._1, cps._1, envs._2, cps._2)
      Foldable[List].foldLeftM(list, (env1,env2)) (step _)
    }
  }
  
  // def valueEquivalent(env1: DeBruijn, v1: Value, env2: DeBruijn, v2: Value): Boolean = {
  //   (v1,v2) match {
  //     case (_: VQuant, _: VQuant) => quantityEquivalent(v1.)
  //     case _ => false
  //   }

}

class DeBruijn(val environment: Map[String,Int], val next: Int){

  def this() = this(Map(), 1)

  def newBindings(bindings: List[String]): DeBruijn = {
    bindings.foldLeft(this) {
      (db: DeBruijn,str: String) =>
      DeBruijn(db.environment + (str -> db.next), db.next + 1)
    }
  }

  def get(key: String): Option[Int] = environment.get(key)

  def equivalent(key1: String, env: DeBruijn, key2: String) = {
    (get(key1), env.get(key2)) match {
      case (None,_) => false
      case (_,None) => false
      case (Some(ix1), Some(ix2)) => ix1 == ix2
      }
  }

}

object DeBruijn{

  def apply(): DeBruijn = new DeBruijn()

  def apply(environment: Map[String,Int], next: Int): DeBruijn = {
    new DeBruijn(environment, next)
  }

  def unapply(db: DeBruijn): Option[(Map[String,Int],Int)] = {
    Some((db.environment, db.next))
  }
  
}