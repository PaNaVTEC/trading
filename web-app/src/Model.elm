module Model exposing (..)

import Dict exposing (Dict)


type alias SocketId =
    String


type alias Symbol =
    String


type alias Price =
    Float


type alias WSUrl =
    String


type AlertType
    = Buy
    | Sell
    | Neutral
    | StrongBuy
    | StrongSell


type alias AlertValue =
    { symbol : Symbol
    , askPrice : Price
    , bidPrice : Price
    , high : Price
    , low : Price
    }


type alias Alert =
    { alertType : AlertType
    , prices : AlertValue
    }


type WsIn
    = Attached SocketId
    | Notification Alert
    | SocketClosed
    | Unknown String


type Msg
    = CloseAlerts
    | Connect
    | SymbolChanged Symbol
    | Subscribe
    | Unsubscribe Symbol
    | Recv WsIn
    | NoOp


type alias Model =
    { symbol : Symbol
    , wsUrl : WSUrl
    , socketId : Maybe SocketId
    , alerts : Dict Symbol Alert
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    , error : Maybe String
    }


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = ""
      , wsUrl = "ws://localhost:9000/ws"
      , socketId = Nothing
      , alerts = Dict.fromList []
      , sub = Nothing
      , unsub = Nothing
      , error = Nothing
      }
    , Cmd.none
    )
