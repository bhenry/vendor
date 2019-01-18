(ns vendor.core-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [vendor.core :refer :all]))

(defn set-state [s]
  (reset! state (merge initial-state s)))

(defn check-state [s]
  (every? identity
    (for [[k v] s]
      (= (@state k) v))))

(defn check-response [response expectation]
  (and (= (:status expectation) (:status response))
       (if (:body expectation)
         (= (json/write-str (:body expectation)) (:body response))
         (nil? (:body response)))
       (every? identity
         (for [[k v] (:headers expectation)]
           (= (get-in response [:headers k]) v)))))

(defn mock-request[& [id body]]
  {:body body
   :params {:id id}})

(deftest inserting-coins
  (testing "It should accept multiple coins one at a time"
    (set-state {:coins 0})
    (doseq [i (range 5)]
      (is (check-response (insert-coin (mock-request nil {"coin" 1}))
                          {:status 204 :headers {"X-Coins" (inc i)}}))
      (is (check-state {:coins (inc i)})))))

(deftest returning-coins
  (testing "It should return the coins"
    (set-state {:coins 3})
    (is (check-response (cancel {}) {:status 204 :headers {"X-Coins" 3}}))
    (is (check-state {:coins 0}))))

(deftest getting-inventory
  (testing "It should report back the inventory"
    (set-state {:items [2 5 3]})
    (is (check-response (list-contents {}) {:status 200 :headers {} :body [2 5 3]})))
  (testing "It should report back inventory of a selection"
    (let [init [2 5 3]]
      (set-state {:items init})
      (doseq [i (range (count init))]
        (is (check-response (check-availability (mock-request i))
                            {:status 200 :headers {} :body (get init i)}))))))

(deftest making-selection
  (testing "It should allow a selection if the funds and inventory are there"
    (set-state {:items [1 1 1] :coins cost :bank 0})
    (is (check-response (choose-item (mock-request 0))
                        {:status 200
                         :headers {"X-Coins" 0 "X-Inventory-Remaining" 0}
                         :body {:quantity 1}}))
    (is (check-state {:bank cost :coins 0 :items [0 1 1]})))
  (testing "It should refund any extra coins"
    (set-state {:items [2 2 2] :coins (+ cost 2) :bank 0})
    (is (check-response (choose-item (mock-request 0))
                        {:status 200
                         :headers {"X-Coins" 2 "X-Inventory-Remaining" 1}
                         :body {:quantity 1}}))
    (is (check-state {:bank cost :coins 0 :items [1 2 2]})))
  (testing "It should deny the selection if the funds are insufficient"
    (doseq [i (range cost)]
      (set-state {:coins i})
      (is (check-response (choose-item (mock-request 0))
                          {:status 403 :headers {"X-Coins" i}}))))
  (testing "It should deny the selection if there are none of the choice left"
    (set-state {:coins 2 :items [0 1 2]})
    (is (check-response (choose-item (mock-request 0))
                        {:status 404 :headers {"X-Coins" 2}}))))
