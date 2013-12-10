(ns com.jayway.rps.eventstore.web
  (:use [environ.core])
  (:require [com.jayway.rps.atom :as a]
            [com.jayway.rps.framework :as f]
            [com.jayway.rps.core :as c]
            [com.jayway.rps.domain :as d]
            [com.jayway.rps.web :as w]
            [clj-http.client :as client]))


(def app
  (let [event-store (a/atom-event-store (env :event-store-uri))]
    (w/create-app 
      (reify com.jayway.rps.core.RockPaperScissors
				(create-game [this player-name]
				  (let [aggregate-id (str "game-" (.toString (java.util.UUID/randomUUID)))]
            (f/handle-command (c/->OnlyCreateGameCommand aggregate-id player-name) event-store)
				    aggregate-id))
				
				(make-move [this game-id player-id move]
          (f/handle-command (c/->DecideMoveCommand game-id player-id move) event-store))
    
        (load-game [this game-id] 
          (let [uri (str (env :event-store-uri) "/projection/games/state?partition=" game-id)
                reply (client/get uri {:as :json})
                game (:body reply)]
            (println game)
            game))))))

