package trading.state

import trading.domain._

import cats.syntax.all._
import derevo.cats._
import derevo.derive
import monocle.Optional
import monocle.function.{ At, Index }
import monocle.macros.GenLens

@derive(eqv, show)
final case class TradeState(
    prices: TradeState.SymbolPrices
) {
  def modify(symbol: Symbol)(action: TradeAction, price: Price, quantity: Quantity): TradeState =
    action match {
      case TradeAction.Ask =>
        val f = Prices._Ask.modify(_.updated(price, quantity))(_)
        val g = Prices._HighAsk.modify(p => if (p < price) price else p)(_)
        val h = Prices._LowAsk.modify(p => if (p > price) price else p)(_)
        val t = f.andThen(g).andThen(h)
        TradeState.__Prices.at(symbol).modify(_.fold(t(Prices.empty))(t).some)(this)
      case TradeAction.Bid =>
        val f = Prices._Bid.modify(_.updated(price, quantity))(_)
        val g = Prices._HighBid.modify(p => if (p < price) price else p)(_)
        val h = Prices._LowBid.modify(p => if (p > price) price else p)(_)
        val t = f.andThen(g).andThen(h)
        TradeState.__Prices.at(symbol).modify(_.fold(t(Prices.empty))(t).some)(this)
    }

  def remove(symbol: Symbol)(action: TradeAction, price: Price): TradeState =
    action match {
      case TradeAction.Ask =>
        TradeState
          .__AskPrices(symbol)
          .modify(_.removed(price))(this)
      case TradeAction.Bid =>
        TradeState
          .__BidPrices(symbol)
          .modify(_.removed(price))(this)
    }
}

@derive(eqv, show)
final case class Prices(
    ask: Prices.Ask,
    bid: Prices.Bid,
    highAsk: AskPrice,
    lowAsk: AskPrice,
    highBid: BidPrice,
    lowBid: BidPrice
)

object Prices {
  def empty: Prices = Prices(Map.empty, Map.empty, 0.0, 0.0, 0.0, 0.0)

  type Ask = Map[AskPrice, Quantity]
  type Bid = Map[BidPrice, Quantity]

  val _Ask     = GenLens[Prices](_.ask)
  val _Bid     = GenLens[Prices](_.bid)
  val _HighAsk = GenLens[Prices](_.highAsk)
  val _LowAsk  = GenLens[Prices](_.lowAsk)
  val _HighBid = GenLens[Prices](_.highBid)
  val _LowBid  = GenLens[Prices](_.lowBid)

  object __AtAsk {
    def apply(p: AskPrice): Optional[Prices, Option[Quantity]] =
      _Ask.andThen(At.atMap[AskPrice, Quantity].at(p))
  }

  object __AtBid {
    def apply(p: BidPrice): Optional[Prices, Option[Quantity]] =
      _Bid.andThen(At.atMap[BidPrice, Quantity].at(p))
  }
}

object TradeState {
  type SymbolPrices = Map[Symbol, Prices]

  def empty: TradeState = TradeState(Map.empty)

  val _Prices = GenLens[TradeState](_.prices)

  object __Prices {
    def at(s: Symbol): Optional[TradeState, Option[Prices]] =
      _Prices.andThen(At.atMap[Symbol, Prices].at(s))

    def index(s: Symbol): Optional[TradeState, Prices] =
      _Prices.andThen(Index.mapIndex[Symbol, Prices].index(s))
  }

  object __AskPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Ask] =
      __Prices.index(s).andThen(Prices._Ask)
  }

  object __BidPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Bid] =
      __Prices.index(s).andThen(Prices._Bid)
  }
}
