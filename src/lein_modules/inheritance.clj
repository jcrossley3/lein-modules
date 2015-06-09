(ns lein-modules.inheritance
  (:use [lein-modules.common :only (config parent without-middleware)]
        [lein-modules.compression :only (compressed-profiles)])
  (:require [leiningen.core.project :as prj]))

(def normalizer (partial map (comp prj/dependency-vec prj/dependency-map)))

(defn normalize-deps
  "Fully-qualifies any dependency vector within the profile map. Only
  required for 2.3.4"
  [m]
  (if (:dependencies m)
    (with-meta (update-in m [:dependencies] normalizer) (meta m))
    m))

(defn filter-profiles
  "We don't want to inherit the non-project-specific profiles or any
  in the leiningen namespace"
  [m]
  (let [ignored [:base :system :user]
        lein-ns (->> m keys (filter #(= "leiningen" (namespace %))))]
    (apply dissoc m (concat ignored lein-ns))))

(defn compositor
  "Returns a reducing function that turns a non-composite profile into
   a composite, e.g. {:test {:a 1}} becomes {:test
   [:test-foo] :test-foo {:a 1}} for a project named 'foo'. Composite
   profiles are simply concatenated"
  [project]
  (fn [m [k v]]
    (cond
      (prj/composite-profile? v) (update-in m [k] (comp vec distinct concat) v)
      (-> v meta ::composited) (assoc m k v)
      :else (let [n (keyword (format "%s%s-%s" (or (namespace k) "") (name k) (:name project)))]
              (assoc (update-in m [k] #(vec (cons n %)))
                n (vary-meta (normalize-deps v) assoc ::composited true))))))

(defn compositize-profiles
  "Return a profile map containing all the profiles found in the
  project and its ancestors, resulting in standard profiles,
  e.g. :test and :dev, becoming composite"
  ([project]
     (compositize-profiles project (compressed-profiles project)))
  ([project active-profiles]
   (without-middleware
     (loop [p project, result nil]
       (if (nil? p)
         result
         (recur (parent p active-profiles)
           (reduce (compositor p) result
             (conj (select-keys (:modules p) [:inherited])
               (filter-profiles (:profiles (meta p)))))))))))

(defn inherit
  "Add profiles from parents, setting any :inherited ones if found,
  where a parent profile overrides a grandparent."
  [project]
  (let [current (compressed-profiles project)
        compost (compositize-profiles project current)]
    (-> (prj/add-profiles project compost)
      (vary-meta update-in [:profiles] merge compost)
      (prj/set-profiles (if (:inherited compost)
                          (cons :inherited current)
                          current)))))
