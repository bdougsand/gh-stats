(ns gh-stats.core
  (:require [environ.core :refer [env]]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]

            [gh-stats.query :refer [defquery make-query]])
  (:gen-class))


(def api-base "https://api.github.com/")

(defn collect-org-stats [org-name]
  (http/get (str api-base "orgs/" org-name "/repos")
            {:headers {"Accept" "application/vnd.github.inertia-preview+json"}
             :query-params {:type "public"}}))

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
      (println "there wuz errs")

      data)))

(defquery org-repos-query [org-name]
  (organization
   {:login org-name}
   (repositories
    {:first 50, :orderBy {:field :UPDATED_AT, :direction :DESC}}
    :totalCount
    (nodes
     :forkCount :url :createdAt :description :updatedAt
     (refs {:refPrefix "refs/heads/"}
           :totalCount)
     (pullRequests {:first 20, :orderBy {:field :CREATED_AT, :direction :DESC}}
                   (nodes
                    (author :login)
                    :createdAt
                    :state
                    :mergedAt
                    :title))))))
