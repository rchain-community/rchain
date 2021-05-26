package coop.rchain.casper.batch1

import org.scalatest.{FlatSpec, Inspectors, Matchers}

class BaseMergeability extends FlatSpec with Matchers with Inspectors with MergeabilityRules {
  it should "!X !X" in MergeableCase(S0)(S0)(Nil)(S0.rstate ++ S0.rstate)
  it should "!X !4" in MergeableCase(S0)(F1)(S1)(S0.rstate)
  it should "!X (!4)" in MergeableCase(S0)(S0, F_)(Nil)(S0.rstate)
  it should "!X !C" in ConflictingCase(S0)(C1)(S1)(S1.rstate ++ S0.rstate)(C1.rstate)
  it should "!X (!C)" in ConflictingCase(S0)(S0, C_)(Nil)(S0.rstate)(C_.rstate)
  it should "!X 4X" in ConflictingCase(S0)(F_)(Nil)(S0.rstate)(F_.rstate)
  it should "!X 4!" in ConflictingCase(S0)(S0)(F_)(emptyState)(emptyState)
  it should "!X 4! 2" in MergeableCase(S1)(S0)(S1, F0)(S1.rstate ++ S1.rstate)
  it should "!X (4!)" in CoveredBy("!X (!4)")
  it should "!X 4!!" in MergeableCase(S1)(R0)(F0)(S1.rstate ++ R0.rstate)
  it should "!X (4!!)" in MergeableCase(S0)(F_, R0)(Nil)(S0.rstate ++ R0.rstate)
  it should "!X !!X" in MergeableCase(S0)(R0)(Nil)(S0.rstate ++ R0.rstate)
  it should "!X !!4" in MergeableCase(S0)(F1)(R1)(S0.rstate ++ R1.rstate)
  it should "!X (!!4)" in CoveredBy("!X (4!!)")
  // it should    "!X !!C"   in InfiniteLoop(S0)(C1)(R1)(S0.rstate ++ R1.rstate)
  // it should "!X (!!C)" in InfiniteLoop(F1)(C0, R0)(S0)(S0.rstate ++ F1.rstate)
  // it should "!X C!!"   in InfiniteLoop(F1)(R1)(S0, C1)(C1.rstate ++ F1.rstate ++ S0.rstate)
  it should "!X CX" in ConflictingCase(S0)(C_)(Nil)(S0.rstate)(C_.rstate)
  it should "!X (C!)" in CoveredBy("!X (!C)")
  // it should    "!4 !4"      in NonDeterminedUnknownCase(F_)(F_)(S1, S0)(emptyState)(emptyState)
  it should "!4 !4" in MergeableCase(F0)(F1)(S0, S1)(emptyState)
  // it should    "!4 (!4)"    in NonDeterminedUnknownCase(F0)(S1, F_)(S0)(emptyState)(emptyState)
  it should "!4 (!4)" in MergeableCase(F0)(S1, F1)(S0)(emptyState)
  it should "(!4) (!4)" in MergeableCase(S0, F_)(S0, F_)(Nil)(emptyState)
  it should "(!4) !4" in CoveredBy("!4 (!4)")
  it should "!4 !C" in MergeableCase(F0)(C1)(S0, S1)(C1.rstate)
  it should "(!4) (!C)" in CoveredBy("(!4) !C")
  it should "(!4) !C" in MergeableCase(F0, S0)(C1)(S1)(C1.rstate)
  it should "!4 (!C)" in MergeableCase(F0)(C1, S1)(S0)(C1.rstate)
  it should "!4 4X" in MergeableCase(F0)(Nil)(S0, F1)(F1.rstate)
  it should "(!4) 4X" in MergeableCase(S0, F_)(Nil)(F1)(F1.rstate)
  it should "!4 4!" in MergeableCase(S0)(F_)(F0, S1)(emptyState)
  it should "(!4) (4!)" in MergeableCase(S0, F_)(S0, F_)(Nil)(emptyState)
  it should "!4 (4!)" in CoveredBy("!4 (!4)")
  it should "(!4) 4!" in MergeableCase(S1, F1)(S0)(F0)(emptyState)
  it should "!4 4!!" in MergeableCase(F0)(R1)(S0, F1)(R1.rstate)
  it should "(!4) 4!!" in MergeableCase(F0, S0)(R1)(F1)(R1.rstate)
  it should "!4 (4!!)" in MergeableCase(F0)(R1, F1)(S0)(R1.rstate)
  it should "(!4) (4!!)" in MergeableCase(F0, S0)(R1, F1)(Nil)(R1.rstate)
  it should "!4 !!X" in MergeableCase(F0)(Nil)(S0, R1)(R1.rstate)
  it should "(!4) !!X" in MergeableCase(F0, S0)(F0)(R1)(R1.rstate ++ F0.rstate)
  // it should    "!4 !!4"     in NonDeterminedUnknownCase(F_)(F_)(S0, R1)(emptyState)(emptyState)
  it should "!4 !!4" in MergeableCase(F0)(F1)(S0, R1)(R1.rstate)
  it should "(!4) !!4" in MergeableCase(F0, S0)(F_)(R1)(R1.rstate)
  //  it should   "!4 (!!4)"   in NonDeterminedConflictCase(F_)(F1, R1)(S0)(emptyState)(R1.rstate ++ S0.rstate)
  it should "!4 (!!4)" in MergeableCase(F0)(F1, R1)(S0)(R1.rstate)
  it should "(!4) (!!4)" in MergeableCase(F0, S0)(F1, R1)(Nil)(R1.rstate)
  // it should   "!4 !!C"     in InfiniteLoop(F0)(C1)(S0, R1)(R1.rstate)
  // it should   "(!4) !!C"   in InfiniteLoop(F0, S0)(C1)(R1)(R1.rstate)
  // it should   "!4 (!!C)"   in InfiniteLoop(F0)(C1, R1)(S0)(emptyState)
  // it should   "(!4) (C!!)" in InfiniteLoop(F0, S0)(R1, C1)(Nil)(emptyState)
  it should "!4 CX" in MergeableCase(F_)(Nil)(S0, C1)(C1.rstate)
  it should "(!4) CX" in MergeableCase(F1, S1)(S1)(C0)(C0.rstate ++ S1.rstate)
  it should "!4 C!" in MergeableCase(F0)(S1)(S0, C1)(C1.rstate)
  it should "!4 (C!)" in ConflictingCase(F0)(C_, S1)(S0)(emptyState)(C_.rstate)
  it should "(!4) (C!)" in MergeableCase(S0, F_)(C_, S0)(Nil)(C_.rstate)
  // it should   "(!4) C!"    in NonDeterminedMergeableCase(S0, F0)(S1)(C_)(C_.rstate)(C_.rstate)
  // it should   "!4 C!!"     in InfiniteLoop(F0)(R1)(S0, C1)(C1.rstate)
  // it should   "(!4) C!!"   in InfiniteLoop(F0, S0)(R1)(C1)(C1.rstate)
  it should "!4 (C!!)" in CoveredBy("!4 (!!C)")
  it should "(!4) (C!!)" in CoveredBy("(!4) (C!!)")
  it should "!C !C" in MergeableCase(C0)(C1)(S0, S1)(C0.rstate ++ C1.rstate)
  it should "!C !C 2" in ConflictingCase(C_)(C_)(S0, S1)(C_.rstate)(C_.rstate)
  it should "!C (!C)" in ConflictingCase(C0)(C_, S1)(S0)(C0.rstate)(C_.rstate)
  it should "!C (!C) 2" in MergeableCase(C0)(C1, S1)(S0)(C0.rstate ++ C1.rstate)
  it should "(!C) !C" in CoveredBy("!C (!C)")
  it should "(!C) (!C)" in MergeableCase(S0, C_)(S0, C_)(Nil)(C_.rstate ++ C_.rstate)
  it should "!C 4X" in MergeableCase(C_)(Nil)(S0, F1)(C_.rstate ++ F1.rstate)
  it should "(!C) 4X" in MergeableCase(S0, C_)(F_)(Nil)(C_.rstate ++ F_.rstate)
  it should "!C 4!" in MergeableCase(C0)(S1)(S0, F1)(C0.rstate)
  // it should  "(!C) 4!"   in NonDeterminedConflictCase(S0, C_)(S0)(F_)(C_.rstate ++ F_.rstate)(emptyState)
  it should "!C (4!)" in MergeableCase(C0)(F1, S1)(S0)(C0.rstate)
  it should "(!C) (4!)" in MergeableCase(S0, C_)(F_, S0)(Nil)(C_.rstate)
  it should "!C 4!!" in CurrentConflictMergeableCase(C0)(R1)(S0, F1)(C0.rstate ++ F1.rstate)(
    R1.rstate ++ S0.rstate
  )
  it should "!C 4!! 2" in ConflictingCase(C_)(R1)(S0, F1)(C_.rstate ++ F1.rstate)(
    R1.rstate ++ S0.rstate
  )
  it should "(!C) 4!!" in CurrentConflictMergeableCase(C0, S0)(R1)(F1)(C0.rstate ++ F1.rstate)(
    R1.rstate
  )
  it should "(!C) 4!! 2" in ConflictingCase(C_, S0)(R1)(F1)(C_.rstate ++ F1.rstate)(R1.rstate)
  it should "!C (4!!)" in CurrentConflictMergeableCase(C0)(R1, F1)(S0)(C0.rstate)(
    R1.rstate ++ S0.rstate
  )
  it should "!C (4!!) 2" in ConflictingCase(C_)(R1, F1)(S0)(C_.rstate)(R1.rstate ++ S0.rstate)
  it should "(!C) (4!!)" in CurrentConflictMergeableCase(C0, S0)(R1, F1)(Nil)(C0.rstate)(R1.rstate)
  it should "(!C) (4!!) 2" in ConflictingCase(C_, S0)(R1, F1)(Nil)(C_.rstate)(R1.rstate)
  it should "!C !!X" in MergeableCase(C0)(Nil)(S0, R1)(C0.rstate ++ R1.rstate)
  it should "(!C) !!X" in MergeableCase(C0, S0)(Nil)(R1)(C0.rstate ++ R1.rstate)
  it should "!C !!4" in MergeableCase(C0)(F1)(S0, R1)(C0.rstate ++ R1.rstate)
  // it should   "!C !!C"       in InfiniteLoop(C0)(C1)(S0, R1)(R1.rstate ++ C0.rstate)
  // it should   "(!C) !!C"     in InfiniteLoop(C0, S0)(C1)(R1)(R1.rstate ++ C0.rstate)
  // it should   "!C (!!C)"     in InfiniteLoop(C0)(C1, R1)(S0)(C0.rstate)
  // it should   "(!C) (!!C)"   in InfiniteLoop(C0, S0)(C1, R1)(Nil)(C0.rstate)
  it should "!C CX" in MergeableCase(C0)(Nil)(S0, C1)(C0.rstate ++ C1.rstate)
  it should "(!C) CX" in MergeableCase(S0, C0)(C_)(Nil)(C0.rstate ++ C_.rstate)
  it should "!C C!" in MergeableCase(S0)(C_)(C0, S1)(C_.rstate ++ C0.rstate)
  it should "(!C) C!" in ConflictingCase(S0, C_)(C_)(S0)(C_.rstate)(C_.rstate)
  it should "!C (C!)" in CoveredBy("!C (!C)")
  it should "(!C) (C!)" in MergeableCase(S0, C_)(C_, S0)(Nil)(C_.rstate ++ C_.rstate)
  // it should   "!C C!!"       in InfiniteLoop(C0)(R1)(S0, C1)(C1.rstate ++ C0.rstate)
  // it should   "(!C) C!!"     in InfiniteLoop(C0, S0)(R1)(C1)(C1.rstate ++ C0.rstate)
  it should "!C (C!!)" in CoveredBy("!C (!!C)")
  it should "(!C) (C!!)" in CoveredBy("(!C) (!!C)")
  it should "4X 4X" in MergeableCase(F_)(F_)(Nil)(F_.rstate ++ F_.rstate)
  it should "4X 4!" in MergeableCase(F0)(F_)(S1)(F0.rstate)
  it should "4X (4!)" in MergeableCase(F_)(F_, S0)(Nil)(F_.rstate)
  it should "4X 4!!" in MergeableCase(S1)(R0)(F0)(S1.rstate ++ R0.rstate)
  it should "4X (4!!)" in CurrentConflictMergeableCase(F1)(R0, F0)(Nil)(F1.rstate)(R0.rstate)
  it should "4X (4!!) 2" in ConflictingCase(F0)(R0, F0)(Nil)(F0.rstate)(R0.rstate)
  it should "4X !!X" in CurrentConflictMergeableCase(S0)(F1)(F1, R0)(
    F1.rstate ++ S0.rstate ++ R0.rstate
  )(F1.rstate ++ F1.rstate ++ R0.rstate)
  it should "4X !!X 2" in ConflictingCase(F_)(R0, F1)(Nil)(F_.rstate)(R0.rstate ++ F1.rstate)
  it should "4X !!4" in MergeableCase(F0)(F1)(R1)(F0.rstate ++ R1.rstate)
  it should "4X (!!4)" in ConflictingCase(F_)(F1, R1)(Nil)(F_.rstate)(R1.rstate)
  // it should   "4X !!C"   in InfiniteLoop(S1)(C1)(F0, R1)(S1.rstate ++ F0.rstate ++ R1.rstate)
  // it should   "4X (!!C)" in InfiniteLoop(S1)(C1, R1)(F0)(S1.rstate ++ F0.rstate)
  it should "4X CX" in MergeableCase(F_)(C_)(Nil)(F_.rstate ++ C_.rstate)
  it should "4X C!" in MergeableCase(F0)(S1)(C1)(F0.rstate ++ C1.rstate)
  it should "4X (C!)" in MergeableCase(F_)(C_, S0)(Nil)(F_.rstate ++ C_.rstate)
  // it should   "4X C!!"     in NonDeterminedMergeableCase(S0)(R0)(F1, C0)(C0.rstate)(C0.rstate)
  it should "4! 4!" in ConflictingCase(S0)(S1)(F_)(emptyState)(emptyState)
  it should "4! 4! 2" in MergeableCase(S0)(S1)(F0, F1)(emptyState)
  it should "(4!) (4!)" in MergeableCase(F_, S0)(F_, S0)(Nil)(emptyState)
  // it should   "(4!) 4!"    in NonDeterminedUnknownCase(F1, S1)(S0)(F_)(emptyState)(emptyState)
  it should "(4!) 4!" in MergeableCase(F1, S1)(S0)(F0)(emptyState)
  it should "4! (4!)" in CoveredBy("(4!) 4!")
  it should "4! 4!!" in MergeableCase(S0)(R1)(F0, F1)(R1.rstate)
  it should "(4!) (4!!)" in MergeableCase(F0, S0)(F1, R1)(Nil)(R1.rstate)
  it should "(4!) 4!!" in MergeableCase(F0, S0)(R1)(F1)(R1.rstate)
  it should "4! (4!!)" in MergeableCase(S0)(F1, R1)(F0)(R1.rstate)
  it should "4! !!X" in MergeableCase(S0)(R1)(F0)(R1.rstate)
  it should "(4!) !!X" in MergeableCase(S0, F0)(R1)(Nil)(R1.rstate)
  it should "4! !!4" in MergeableCase(S1)(F0)(F1, R0)(R0.rstate)
  it should "(4!) !!4" in MergeableCase(S1, F1)(F0)(R0)(R0.rstate)
  it should "(4!) (!!4)" in CoveredBy("(4!) (4!!)")
  it should "4! (!!4)" in MergeableCase(S1)(F0, R0)(F1)(R0.rstate)
  // it should   "4! !!C"     in InfiniteLoop(S1)(C0)(F1, R0)(R0.rstate)
  // it should   "(4!) !!C"   in InfiniteLoop(S1, F1)(C0)(R0)(R0.rstate)
  // it should   "4! (!!C)"   in InfiniteLoop(S1)(C0, R0)(F1)(emptyState)
  // it should   "(4!) (!!C)" in InfiniteLoop(S1, F1)(C0, R0)(Nil)(emptyState)
  it should "4! CX" in MergeableCase(S0)(C1)(F_)(C1.rstate)
  it should "(4!) CX" in MergeableCase(F_, S0)(C_)(Nil)(C_.rstate)
  // it should   "4! C!"      in NonDeterminedConflictCase(S0)(S1)(F_, C_)(emptyState)(emptyState)
  it should "4! C!" in MergeableCase(S0)(S1)(F0, C1)(C1.rstate) // double check
  // it should   "4! (C!)"    in NonDeterminedConflictCase(S0)(C_, S0)(F_)(emptyState)(C_.rstate)
  it should "(4!) C!" in MergeableCase(F1, S1)(S0)(C0)(C0.rstate)
  // it should   "(4!) C!"    in NonDeterminedConflictCase(F1, S1)(S0)(C_)(C_.rstate)(C_.rstate)
  it should "(4!) (C!)" in MergeableCase(F_, S0)(C_, S0)(Nil)(C_.rstate)
  // it should   "4! C!!"      in InfiniteLoop(S1)(R0)(F1, C0)(C0.rstate)
  // it should   "(4!) C!!"    in InfiniteLoop(S1, F1)(R0)(C0)(C0.rstate)
  it should "4! (C!!)" in CoveredBy("4! (!!C)")
  it should "(4!) (C!!)" in CoveredBy("(4!) (!!C)")
  it should "4!! 4!!" in ConflictingCase(R1)(R0)(F_)(R1.rstate)(R0.rstate)
  it should "4!! 4!! 2" in MergeableCase(R1)(R0)(F1, F0)(R1.rstate ++ R0.rstate)
  it should "(4!!) (4!!)" in MergeableCase(R1, F1)(R0, F0)(Nil)(R1.rstate ++ R0.rstate)
  it should "(4!!) 4!!" in ConflictingCase(R1, F1)(R0)(F_)(R1.rstate)(R0.rstate)
  it should "(4!!) 4!! 2" in MergeableCase(R1, F1)(R0)(F0)(R1.rstate ++ R0.rstate)
  it should "4!! (4!!)" in ConflictingCase(R1)(R0, F0)(F_)(R1.rstate)(R0.rstate)
  it should "4!! !!X" in MergeableCase(R1)(Nil)(F1, R0)(R1.rstate ++ R0.rstate)
  it should "4!! !!X 2" in ConflictingCase(R1)(F1)(F1, R0)(R1.rstate ++ R0.rstate)(
    F1.rstate ++ F1.rstate ++ R0.rstate
  )
  it should "(4!!) !!X" in ConflictingCase(R1, F1)(F1)(R0)(R1.rstate ++ R0.rstate)(
    R0.rstate ++ F1.rstate
  )
  it should "4!! !!4" in MergeableCase(R0)(F1)(F0, R1)(R1.rstate ++ R0.rstate)
  it should "4!! !!4 2" in MergeableCase(R0)(F_)(F0, R1)(R1.rstate ++ R0.rstate)
  it should "(4!!) !!4" in MergeableCase(R0, F0)(F1)(R1)(R1.rstate ++ R0.rstate)
  it should "(4!!) !!4 2" in MergeableCase(R0, F_)(F1)(R1)(R1.rstate ++ R0.rstate)
  it should "4!! (!!4)" in CoveredBy("(4!!) !!4")
  it should "(4!!) (!!4)" in MergeableCase(R0, F0)(F1, R1)(Nil)(R1.rstate ++ R0.rstate)
  it should "(4!!) (!!4) 2" in MergeableCase(R0, F_)(F1, R1)(Nil)(R1.rstate ++ R0.rstate)
  // it should   "4!! !!C"     in InfiniteLoop(R1)(C0)(F1, R0)(R1.rstate ++ R0.rstate)
  // it should   "(4!!) !!C"   in InfiniteLoop(R1, F1)(C0)(R0)(R1.rstate ++ R0.rstate)
  // it should   "4!! (!!C)"   in InfiniteLoop(R1)(C0, R0)(F1)(R1.rstate)
  // it should   "(4!!) (!!C)" in InfiniteLoop(R1, F1)(C0, R0)(Nil)(R1.rstate)
  it should "4!! CX" in CurrentConflictMergeableCase(R1)(C0)(F1)(R1.rstate)(C0.rstate ++ F1.rstate)
  it should "4!! CX 2" in ConflictingCase(R1)(C_)(F1)(R1.rstate)(C_.rstate ++ F1.rstate)
  it should "(4!!) CX" in ConflictingCase(R1, F1)(C_)(Nil)(R1.rstate)(C_.rstate)
  it should "(4!!) CX 2" in CurrentConflictMergeableCase(R1, F1)(C0)(Nil)(R1.rstate)(C0.rstate)
  it should "4!! C!" in MergeableCase(R1)(S0)(F1, C0)(R1.rstate ++ C0.rstate)
  // it should   "(4!!) C!"     in NonDeterminedConflictCase(R1, F_)(C0)(S0)(R1.rstate)(C0.rstate)
  // it should   "(4!!) C! 2"   in NonDeterminedMergeableCase(R1, F_)(C_)(S0)(emptyState)(emptyState)
  it should "4!! (C!)" in CurrentConflictMergeableCase(R1)(C0, S0)(F1)(R1.rstate)(
    C0.rstate ++ F1.rstate
  )
  it should "4!! (C!) 2" in ConflictingCase(R1)(C_, S0)(F1)(R1.rstate)(C_.rstate ++ F1.rstate)
  it should "(4!!) (C!)" in CurrentConflictMergeableCase(R1, F1)(C0, S0)(Nil)(R1.rstate)(C0.rstate)
  it should "(4!!) (C!) 2" in ConflictingCase(R1, F1)(C_, S0)(Nil)(R1.rstate)(C_.rstate)
  ///@@@@@@@@@@@@@
  // things needs to be confirm in "@0!!(0) | @!(0)"
  // it should   "4!! C!!"      in InfiniteLoop(R1)(R0)(F1, C0)(R1.rstate ++ C0.rstate)
  // it should   "(4!!) C!!"    in InfiniteLoop(R1, F1)(R0)(C0)(R1.rstate ++ C0.rstate)
  it should "4!! (C!!)" in CoveredBy("4!! (!!C)")
  it should "(4!!) (C!!)" in CoveredBy("(4!!) (!!C)")
  it should "!!X !!X" in MergeableCase(Nil)(Nil)(R0, R1)(R0.rstate ++ R1.rstate)
  it should "!!X !!4" in MergeableCase(Nil)(F1)(R0, R1)(R0.rstate ++ R1.rstate)
  it should "!!X (!!4)" in MergeableCase(Nil)(F1, R1)(R0)(R0.rstate ++ R1.rstate)
  // it should   "!!X !!C"      in InfiniteLoop(R0)(C1)(R1)(R0.rstate ++ R1.rstate)
  // it should   "!!X (!!C)"    in InfiniteLoop(R0)(C1, R1)(Nil)(R0.rstate)
  it should "!!X CX" in ConflictingCase(R0)(C_)(Nil)(R0.rstate)(C_.rstate)
  it should "!!X C!" in MergeableCase(F1)(S1)(R0, C1)(R0.rstate ++ F1.rstate ++ C1.rstate)
  it should "!!X (C!)" in MergeableCase(F1)(S1, C1)(R0)(R0.rstate ++ F1.rstate ++ C1.rstate)
  // it should   "!!X C!!"      in InfiniteLoop(R0)(R1)(C1)(R0.rstate ++ C1.rstate)
  it should "!!X (C!!)" in CoveredBy("!!X (!!C)")
  it should "!!4 !!4" in MergeableCase(F0)(F1)(R1, R0)(R1.rstate ++ R0.rstate)
  it should "!!4 !!4 2" in MergeableCase(F_)(F1)(R1, R0)(R1.rstate ++ R0.rstate)
  it should "!!4 (!!4)" in ConflictingCase(F1)(F_, R1)(R0)(F1.rstate ++ R0.rstate)(
    R1.rstate ++ R0.rstate
  )
  it should "(!!4) !!4" in CoveredBy("!!4 (!!4)") // new
  it should "(!!4) (!!4)" in MergeableCase(F1, R1)(F0, R0)(Nil)(R1.rstate ++ R0.rstate)
  // it should   "!!4 !!C"      in InfiniteLoop(F1)(C0)(R1, R0)(R1.rstate ++ R0.rstate)
  it should "(!!4) !!C" in CoveredBy("(4!!) !!C")
  // it should   "!!4 (!!C)"    in InfiniteLoop(F1)(C0, R0)(R1)(R1.rstate)
  it should "(!!4) (!!C)" in CoveredBy("(4!!) (!!C)")
  it should "!!4 CX" in MergeableCase(F0)(Nil)(R0, C1)(R0.rstate ++ C1.rstate)
  it should "(!!4) CX" in ConflictingCase(F0, R0)(C0)(Nil)(R0.rstate)(C0.rstate)
  it should "(!!4) CX 2" in MergeableCase(F0, R0)(Nil)(C1)(R0.rstate ++ C1.rstate)
  it should "!!4 C!" in MergeableCase(F0)(S1)(R0, C1)(R0.rstate ++ C1.rstate)
  it should "(!!4) C!" in CurrentConflictMergeableCase(F0, R0)(S1, C1)(Nil)(R0.rstate)(C1.rstate)
  it should "!!4 (C!)" in MergeableCase(F0)(S1, C1)(R0)(R0.rstate ++ C1.rstate)
  it should "(!!4) (C!)" in CurrentConflictMergeableCase(F0, R0)(S1, C1)(Nil)(R0.rstate)(C1.rstate)
  it should "(!!4) (C!) 2" in ConflictingCase(F0, R0)(S1, C_)(Nil)(R0.rstate)(C_.rstate)
  // it should   "!!4 C!!"      in InfiniteLoop(F0)(R1)(R0, C1)(R0.rstate ++ C1.rstate)
  it should "(!!4) C!!" in CoveredBy("(4!!) C!!")
  it should "!!4 (C!!)" in CoveredBy("!!4 (!!C)")
  it should "(!!4) (C!!)" in CoveredBy("(4!!) (!!C)")
  // it should   "!!C !!C"      in InfiniteLoop(C1)(C0)(R0, R1)(R0.rstate ++ R1.rstate)
  // it should   "(!!C) !!C"    in InfiniteLoop(C1, R1)(C0)(R0)(R0.rstate)
  // it should   "(!!C) (!!C)"  in InfiniteLoop(C1, R1)(C0, R0)(Nil)(emptyState)
  // it should   "!!C CX"       in InfiniteLoop(C0)(C1)(R0)(R0.rstate ++ C1.rstate)
  // it should   "(!!C) CX"     in InfiniteLoop(C0, R0)(C1)(Nil)(C1.rstate)
  // it should   "!!C C!"       in InfiniteLoop(C0)(S1)(R0, C1)(R0.rstate ++ C1.rstate)
  // it should   "(!!C) C!"     in InfiniteLoop(C0, R0)(S1)(C1)(C1.rstate)
  // it should   "!!C (C!)"     in InfiniteLoop(C0)(S1, C1)(R0)(R0.rstate ++ C1.rstate)
  // it should   "(!!C) (C!)"   in InfiniteLoop(C0, R0)(S1, C1)(Nil)(C1.rstate)
  // it should   "!!C C!!"      in InfiniteLoop(C0)(R1)(R0, C1)(R0.rstate ++ C1.rstate)
  // it should   "(!!C) C!!"    in InfiniteLoop(C0, R0)(R1)(C1)(C1.rstate)
  it should "(!!C) (C!!)" in CoveredBy("(!!C) (!!C)")
  it should "CX CX" in MergeableCase(C0)(C1)(Nil)(C0.rstate ++ C1.rstate)
  it should "CX C!" in MergeableCase(S1)(S0)(C0)(S1.rstate ++ C0.rstate)
  it should "CX (C!)" in MergeableCase(C_)(C_, S0)(Nil)(C_.rstate ++ C_.rstate)
  // it should   "CX C!!"       in InfiniteLoop(C0)(R1)(C1)(C0.rstate ++ C1.rstate)
  // it should   "CX (C!!)"     in InfiniteLoop(C0)(R1, C1)(Nil)(C0.rstate)
  it should "C! C!" in MergeableCase(S1)(S0)(C_)(C_.rstate)
  it should "C! C! 2" in MergeableCase(S0)(S1)(C0, C1)(C0.rstate ++ C1.rstate)
  it should "(C!) C!" in MergeableCase(C0, S0)(S0)(C_)(C_.rstate ++ C0.rstate)
  it should "C! (C!)" in CoveredBy("(C!) C!") // ?
  it should "(C!) (C!)" in MergeableCase(C_, S0)(C_, S0)(Nil)(C_.rstate ++ C_.rstate)
  // it should   "C! C!!"       in InfiniteLoop(S1)(R0)(C1, C0)(C1.rstate ++ C0.rstate)
  it should "(C!) C!!" in CoveredBy("(!C) C!!")
  // it should   "C! (C!!)"     in InfiniteLoop(S1)(R0, C0)(C1)(C1.rstate)
  it should "(C!) (C!!)" in CoveredBy("(!C) (C!!)")
  // it should   "C!! C!!"      in InfiniteLoop(R0)(R1)(C1, C0)(C1.rstate ++ C0.rstate)
  it should "(C!!) C!!" in CoveredBy("(!!C) C!!")
  it should "(C!!) (C!!)" in CoveredBy("(!!C) (C!!)")
}
