(ns gh-stats.query
  (:require [clojure.string :as str]))

(declare query-termlist make-query-from-spec)

(defn query-term
  ([term]
   (cond (or (symbol? term)
             (list? term)) [(list `apply `str (list `query-term term))]
         (keyword? term) [(name term)]
         (string? term) ["\"" term "\""]
         (map? term) (concat ["{"] (query-termlist term) ["}"])
         (nil? term) []
         :else [(str term)])))

(defn query-termlist [terms]
  (apply concat
   (interpose [", "]
              (map (fn [[k v]]
                     (concat [(name k) ": "] (query-term v)))
                   terms))))

(defn query-from-specs [specs]
  (apply concat ["\n"]
         (interpose ["\n"] (map make-query-from-spec specs))))

(defn make-query-from-spec [spec]
  (cond (or (list? spec)
            (vector? spec))
        (let [[term & [opts :as subquery]] spec
              opts (and (map? opts) opts)]
          (concat [(name term)]
                  (when opts (concat ["("]
                                     (query-termlist opts)
                                     [")"]))
                  [" {"]
                  (query-from-specs (if opts (rest subquery) subquery))
                  ["\n}"]))

        :else (query-term spec)))

(defn merge-adjacent-strings [coll]
  (mapcat (fn [[x :as xs]]
            (if (string? x)
              [(apply str xs)]
              xs))
          (partition-by string? coll)))

(defn make-query [specs]
  (str "query {\n" (apply str (mapcat make-query-from-spec specs)) "\n}"))

(defmacro defquery [name params & specs]
  `(defn ~name ~params
     (str "query {\n" ~@(merge-adjacent-strings
                         (mapcat make-query-from-spec specs)) "\n}")))
