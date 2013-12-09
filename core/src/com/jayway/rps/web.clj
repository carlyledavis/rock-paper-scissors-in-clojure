(ns com.jayway.rps.web
  (:use [compojure.core] 
        [environ.core]
        [ring.middleware.session]
        [ring.middleware.keyword-params]
        [ring.middleware.params]
        [hiccup.core]
        [com.jayway.rps.facebook])
  (:require [compojure.route :as route]
            [com.jayway.rps.core :as c]))

(def rps (reify com.jayway.rps.core.RockPaperScissors
           (create-game [this] "NEW")
           (perform-command [this command] (println command))
           (load-game [this game-id] (println "load " game-id))))

(defn create-game [player-id]
  (let [aggregate-id (c/create-game rps)]
    (c/perform-command rps (c/->OnlyCreateGameCommand aggregate-id player-id))
    aggregate-id))

(defn make-move [game-id player-id move]
  (c/perform-command rps (c/->DecideMoveCommand game-id player-id move)))

(defn get-user [request]
  (get-in request [:session :me :name]))

(defn render-move-form 
  ([]
    (render-move-form "/games" "Create game"))
  ([game-id]
    (render-move-form (str "/games/" game-id) "Make move"))
  ([uri button-text]
    [:form {:action uri :method "post"} 
              [:select {:name "move"}
               [:option {:value "rock"} "Rock"]
               [:option {:value "paper"} "Paper"]
               [:option {:value "scissors"} "Scissors"]]
              [:input {:type "submit" :value button-text}]]))

(defn render-create-game-form 
  []
  [:form {:action "/games" :method "post"} 
   [:input {:type "submit" :value "Create game"}]])

(defn render-moves [moves]
  [:ul (map (fn [[player move]] [:li (str (name player) " moved " move)]) moves)])

(defn render-game [game-id player-id]
  (let [game (c/load-game rps game-id)]
    (html [:body
           [:p (str "Created by " (:creator game))]
           (condp = (:state game)
             "open" (if-not (get-in game [:moves (keyword player-id)])
                      (render-move-form game-id)
                      [:p "Waiting..."])
             "won" [:div [:p (str "The winner is " (:winner game))] (render-moves (:moves game))]
             "tied" [:div [:p "Tie!"] (render-moves (:moves game))]
             "???")])))

(defroutes handler
  (GET "/" [] (html [:body (render-create-game-form)]))
  (GET "/games" [] (html [:body (render-create-game-form)]))
  (POST "/games" [:as r] 
        (let [game-id (create-game (get-user r))]
          (ring.util.response/redirect-after-post (str "/games/" game-id))))
  (POST "/games/:game-id" [game-id move :as r] 
        (make-move game-id (get-user r) move)
        (ring.util.response/redirect-after-post (str "/games/" game-id)))
  (GET "/games/:game-id" [game-id :as r] (render-game game-id (get-user r)))
  (route/not-found "<h1>Page not found</h1>"))

(defn wrap-log [handler level]
  (fn [request]
	  (println "REQUEST " level " : " request)
	  (let [response (handler request)]
      (println "RESPONSE " level " : " response)
      response)))

(defn create-app [implementation]
  (def rps implementation)
  (-> handler 
      wrap-require-facebook-login
      wrap-session 
      wrap-keyword-params
      wrap-params))

(defn -main [& args]
 (let [game-id (c/create-game rps)]
   (c/perform-command rps (c/->CreateGameCommand game-id "player-1" "rock"))
   (c/perform-command rps (c/->DecideMoveCommand game-id "player-2" "scissors"))))