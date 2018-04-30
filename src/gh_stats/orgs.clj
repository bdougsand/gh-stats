(ns gh-stats.orgs
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str])
  (:import [java.nio.file Paths]))

(def brigade-list "https://brigade.codeforamerica.org/brigades")


(def page (html/html-resource (java.net.URL. brigade-list)))

(defn resolve-cfa-link [rel-url]
  (str "https://brigade.codeforamerica.org" rel-url))

(defn brigade-links [page]
  (map (juxt html/text (comp resolve-cfa-link :href :attrs))
       (html/select page [:section.brigade-list :li :a])))

(defn get-page-socials [page]
  (let [links (html/select page [:a.button])]
    (into {} (keep (fn [{{:keys [title href]} :attrs}]
                     (when-let [[_ social] (when title (re-find #"Brigade social: (.*)" title))]
                       [(str/lower-case social) href])))
          links)))

(defn get-socials [url]
  (get-page-socials (html/html-resource (java.net.URL. url))))

(defn collect-brigades [page]
  (into {}
        (for [[org-name org-link] (brigade-links page)]
          (do (Thread/sleep 250)
              [org-name (get-socials org-link)]))))

(comment
  (def brigade-info (collect-brigades page)))

(defn fix-keys [m]
  (into {} (map (fn [[k v]] [(keyword (str/lower-case k)) v])) m))
(map #(get (val %) "github") brigade-info)
(spit "resources/brigades.edn" (pr-str brigade-info))
(comment
  (spit "resources/brigades.edn" (pr-str brigade-info)))
