package trading.alerts

import trading.core.EventSource
import trading.core.snapshots.SnapshotReader
import trading.domain._
import trading.events.TradeEvent
import trading.events.TradeEvent.CommandExecuted
import trading.lib.Producer
import trading.state.TradeState

import cats.Applicative
import cats.syntax.all._
import fs2.{ Pipe, Stream }

trait AlertEngine[F[_]] {
  def run: Pipe[F, TradeEvent, Unit]
}

object AlertEngine {
  def make[F[_]: Applicative](
      producer: Producer[F, Alert],
      snapshots: SnapshotReader[F]
  ): AlertEngine[F] =
    new AlertEngine[F] {
      val run: Pipe[F, TradeEvent, Unit] =
        events =>
          Stream.eval(snapshots.latest).flatMap { maybeSt =>
            events
              .evalMapAccumulate(maybeSt.getOrElse(TradeState.empty)) { case (st, CommandExecuted(cmd, _)) =>
                val nst = EventSource.runS(st)(cmd)
                val p   = st.prices.get(cmd.symbol)
                val c   = nst.prices.get(cmd.symbol)

                val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
                val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)
                val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
                val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)

                val highAsk: AskPrice = c.map(_.highAsk).getOrElse(0.0)
                val lowAsk: AskPrice  = c.map(_.lowAsk).getOrElse(0.0)
                val highBid: BidPrice = c.map(_.highBid).getOrElse(0.0)
                val lowBid: BidPrice  = c.map(_.lowBid).getOrElse(0.0)

                // dummy logic to simulate the trading market
                val alert: Option[Alert] =
                  if (previousAskMax - currentAskMax > 0.3)
                    Alert.StrongBuy(cmd.symbol, currentAskMax, currentBidMax, highAsk, highBid, lowAsk, lowBid).some
                  else if (previousAskMax - currentAskMax > 0.2)
                    Alert.Buy(cmd.symbol, currentAskMax, currentBidMax, highAsk, highBid, lowAsk, lowBid).some
                  else if (currentBidMax - previousBidMax > 0.3)
                    Alert.StrongSell(cmd.symbol, currentAskMax, currentBidMax, highAsk, highBid, lowAsk, lowBid).some
                  else if (currentBidMax - previousBidMax > 0.2)
                    Alert.Sell(cmd.symbol, currentAskMax, currentBidMax, highAsk, highBid, lowAsk, lowBid).some
                  else
                    none[Alert]
                //Alert.Neutral(cmd.symbol, currentAskMax, currentBidMax)

                alert.traverse_(producer.send).tupleLeft(nst)
              }
              .void
          }
    }
}
