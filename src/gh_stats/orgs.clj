(ns gh-stats.orgs
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.nio.file Paths]))

(def brigade-list "https://brigade.codeforamerica.org/brigades")

(def missing-socials
  {"Code for Boston" {:github "https://github.com/codeforboston"}
   "BetaNYC" {:github "https://github.com/BetaNYC"}})


(defn resolve-cfa-link [rel-url]
  (str "https://brigade.codeforamerica.org" rel-url))

(defn brigade-links [page]
  (map (juxt html/text (comp resolve-cfa-link :href :attrs))
       (html/select page [:section.brigade-list :li :a])))

(defn get-page-socials [page]
  (let [links (html/select page [:a.button])]
    (into {} (keep (fn [{{:keys [title href]} :attrs}]
                     (when-let [[_ social] (when title (re-find #"Brigade social: (.*)" title))]
                       [(keyword (str/lower-case social)) href])))
          links)))

(defn get-socials [url]
  (get-page-socials (html/html-resource (java.net.URL. url))))

(defn collect-brigades [page]
  (into {}
        (for [[org-name org-link] (brigade-links page)]
          (do (Thread/sleep 250)
              [org-name (get-socials org-link)]))))

(defn load-brigades-from-file
  ([path]
   (try
     (with-open [in (java.io.PushbackReader. (io/reader path))]
       (clojure.edn/read in))

     (catch java.io.FileNotFoundException _
       nil)))
  ([]
   (load-brigades-from-file "resources/brigades.edn")))

(defn load-brigades-from-site
  ([url]
   (-> (java.net.URL. brigade-list)
       (html/html-resource)
       (collect-brigades)))
  ([]
   (load-brigades-from-site brigade-list)))

(def brigade-info
  (delay (or (load-brigades-from-file)
             (let [info (merge-with merge missing-socials (load-brigades-from-site))]
               (spit "resources/brigades.edn" (pr-str info))
               info))))
