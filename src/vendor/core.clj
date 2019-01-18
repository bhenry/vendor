(ns vendor.core
  (:gen-class)
  (:require [clojure.data.json :as json]
            [compojure.core :refer [PUT DELETE GET context defroutes]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]))

(def cost 2)
(def capacity 5)
(def doors 3)
(def initial-state {:coins 0 :bank 0 :items (vec (repeat doors capacity))})
(def state (atom initial-state))

(defn response [code & [body headers]]
  {:status code
   :headers (merge {"Content-Type" "application/json"} headers)
   :body (when body (json/write-str body))})

(defn get-id [req]
  (-> req :params :id str Integer/parseInt))

(defn deposit-coin []
  (:coins (swap! state update :coins inc)))

(defn insert-coin [req]
  (if (-> req :body (get "coin") (= 1))
    (response 204 nil {"X-Coins" (deposit-coin)})
    (response 500 "Coin Error")))

(defn return-coins []
  ;; side-effecty mechanical stuff drops coins
  (swap! state update :coins (constantly 0)))

(defn cancel [req]
  (let [coins (@state :coins)]
    (return-coins)
    (response 204 nil {"X-Coins" coins})))

(defn list-contents [req]
  (response 200 (@state :items)))

(defn remaining-inventory [id]
  (get-in @state [:items id] 0))

(defn check-availability [req]
  (response 200 (remaining-inventory (get-id req))))

(defn purchase [id]
  ;; side effecty mechanical stuff banks 2 coins
  (swap! state (fn [s]
                 (-> s
                     (update :bank + cost)
                     (update :coins - cost)))))

(defn dispense [id]
  ;; side effecty mechanical stuff drops drinks
  (swap! state update-in [:items id] dec))

(defn make-selection [id]
  (purchase id)
  (dispense id)
  (let [coins (:coins @state)]
    (return-coins)
    (response 200 {"quantity" 1}
      {"X-Coins" coins
       "X-Inventory-Remaining" (remaining-inventory id)})))

(defn choose-item [req]
  (let [selection (get-id req)
        coins (:coins @state)]
    (if (>= coins cost)
      (if (pos? (remaining-inventory selection))
        (make-selection selection)
        (response 404 nil {"X-Coins" coins}))
      (response 403 nil {"X-Coins" coins}))))

(defn error404 [req]
  (response 404 "Not found"))

(defroutes all-routes
  (PUT "/" [] insert-coin)
  (DELETE "/" [] cancel)
  (GET "/inventory" [] list-contents)
  (context "/inventory/:id" []
           (GET "/" [] check-availability)
           (PUT "/" [] choose-item))
  (route/not-found error404))

(defn middleware [app]
  (-> app
      wrap-json-body
      wrap-json-response))

(defn -main [& args]
  (run-server (middleware all-routes) {:port 8080})
  (println "Server started on port 8080")
  (println "Direct requests to http://localhost:8080/"))
