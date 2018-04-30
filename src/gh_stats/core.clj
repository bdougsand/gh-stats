(ns gh-stats.core
  (:require [environ.core :refer [env]]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]

            [gh-stats.query :refer [defquery make-query]]
            [clojure.instant :refer [read-instant-timestamp]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))


(def api-base "https://api.github.com/")

(def ^:dynamic *github-token*
  (env :github-access-token))

(defn do-graphql-query
  ([query token]
   (http/post (str api-base "graphql")
              {:body (json/write-str {:query query})
               :headers {"Authorization" (str "bearer " token)}}))
  ([query]
   (do-graphql-query query *github-token*)))

(defn graphql-query [& args]
  (let [resp @(apply do-graphql-query args)
        {:keys [data errors]} (json/read-str (:body resp) :key-fn keyword)]
    (if errors
      (throw (ex-info "The returned message contained errors"
                      {:data data, :errors errors, :response resp}))

      data)))

(defquery org-repos-query [org-name]
  [:organization {:login org-name}
   [:repositories {:first 50, :orderBy {:field :UPDATED_AT, :direction :DESC}}
    :totalCount
    [:nodes
     :name :url :forkCount :createdAt :description :updatedAt
     [:refs {:refPrefix "refs/heads/", :first 50} :totalCount
      [:nodes :name]]
     [:pullRequests {:first 50, :orderBy {:field :CREATED_AT, :direction :DESC}}
      [:nodes
       [:author :login]
       :createdAt :state :mergedAt :title]]]]])

(defn get-org-data [org]
  (graphql-query (org-repos-query org)))

(defn make-repo-record [repo]
  (let [unique-authors (into #{} (map #(get-in % [:author :login]))
                             (-> repo :pullRequests :nodes))]
    (merge (select-keys repo [:name :url :forkCount :description])
           {:createdAt (read-instant-timestamp (:createdAt repo))
            :updatedAt (read-instant-timestamp (:updatedAt repo))
            :pullRequests (get-in repo [:pullRequests :totalCount] 0)
            :prAuthors unique-authors
            :prAuthorCount (count unique-authors)
            :branchCount (get-in repo [:refs :totalCount] 0)
            :branches (into [] (map :name) (-> repo :refs :nodes))})))

(defn make-org-records [data]
  (map make-repo-record (-> data :organization :repositories :nodes)))

(defn collect-org-records [orgs]
  (into {} (map (juxt identity (comp make-org-records get-org-data))) orgs))
