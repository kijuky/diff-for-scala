package io.github.kijuky.diff

import com.github.difflib.patch.{AbstractDelta, Chunk, Patch}

import scala.collection.JavaConverters._

object Implicits {

  /** 行番号。有効な値は1以上の整数 */
  type LineNumber = Int

  implicit class RichOptionLineNumber(optLineNumber: Option[LineNumber]) {
    def toInteger: Integer = optLineNumber.map(Integer.valueOf).orNull
  }

  implicit class RichPatch[T](patch: Patch[T]) {
    def deltas: Seq[AbstractDelta[T]] = patch.getDeltas.asScala.toSeq
    def sourceLineNumber(targetLineNumber: LineNumber): Option[LineNumber] = {
      require(targetLineNumber > 0)
      deltas
        .foldLeft[Option[LineNumber]](Some(0)) { case (result, delta) =>
          result match {
            case None =>
              // 古いソースコードに対応する行が存在しない。
              result
            case Some(result) =>
              if (delta.target.changedLineNumber.contains(targetLineNumber)) {
                // 新規追加行なので、古いソースコードに対応する行が存在しない。
                None
              } else {
                val th = delta.target.headLineNumber
                val tl = delta.target.lastLineNumber
                val sh = delta.source.headLineNumber
                val sl = delta.source.lastLineNumber
                if (targetLineNumber <= th) {
                  // delta より前の行
                  Some(if (result != 0) {
                    // 既に決定しているので、result を返す。
                    result
                  } else {
                    targetLineNumber + sh - th
                  })
                } else if (tl <= targetLineNumber) {
                  // delta より後の行
                  Some(targetLineNumber + sl - tl)
                } else {
                  // delta の中の行
                  delta.sourceLineNumber(targetLineNumber)
                }
              }
          }
        }
        .ensuring(_.forall(_ > 0))
    }
  }

  implicit class RichAbstractDelta[T](delta: AbstractDelta[T]) {
    def target: Chunk[T] = delta.getTarget
    def source: Chunk[T] = delta.getSource
    def sourceLineNumber(targetLineNumber: LineNumber): Option[LineNumber] = {
      val targetUnchanged = target.unchangedLineNumber
      val sourceUnchanged = source.unchangedLineNumber
      assume(targetUnchanged.size == sourceUnchanged.size)
      targetUnchanged.indexOf(targetLineNumber) match {
        case -1    => None
        case index => Some(sourceUnchanged(index))
      }
    }
  }

  implicit class RichChunk[T](chunk: Chunk[T]) {
    def lastLineNumber: LineNumber = chunk.last() + 1
    def headLineNumber: LineNumber = chunk.getPosition + 1
    def changedLineNumber: Seq[LineNumber] =
      chunk.getChangePosition.asScala.toSeq.map(_.toInt)
    def unchangedLineNumber: Seq[LineNumber] =
      (headLineNumber to lastLineNumber).filterNot(changedLineNumber.contains)
  }
}
