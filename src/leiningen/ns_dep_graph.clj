(ns leiningen.ns-dep-graph
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.track :as ns-track]
            [clojure.tools.namespace.find :as ns-find]
            [clojure.tools.namespace.dependency :as ns-dep]
            [rhizome.viz :as viz]))

(def default-options {"-name"     "ns-dep-graph"
                      "-platform" ":clj"})

(defn ns-dep-graph
  "Create a namespace dependency graph and save it as either ns-dep-graph or the supplied name."
  [project & args]
  (let [built-args (merge default-options (apply hash-map args))
        file-name (get built-args "-name")
        platform (case (edn/read-string (get built-args "-platform"))
                   :clj ns-find/clj
                   :cljs ns-find/cljs
                   ns-find/clj)
        source-files (apply set/union
                            (map (comp #(ns-find/find-sources-in-dir % platform)
                                       io/file)
                                 (project :source-paths)))
        tracker (ns-file/add-files {} source-files)
        dep-graph (tracker ::ns-track/deps)
        ns-names (set (map (comp second ns-file/read-file-ns-decl)
                           source-files))
        part-of-project? (partial contains? ns-names)
        nodes (filter part-of-project? (ns-dep/nodes dep-graph))]
    (loop [name file-name
           counter 1]
      (if (.exists (io/file (str name ".png")))
        (recur (str file-name counter) (inc counter))
        (viz/save-graph
         nodes
         #(filter part-of-project? (ns-dep/immediate-dependencies dep-graph %))
         :node->descriptor (fn [x] {:label x})
         :options {:dpi 72}
         :filename (str name ".png"))))))

;; TODO: maybe add option to show dependencies on external namespaces as well.
