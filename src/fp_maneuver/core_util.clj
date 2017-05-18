(in-ns 'fp-maneuver.core)

;; (let [stream
;;       (-> (ProcessBuilder. ["cmd" "/c" "dir"])
;;           .start .getInputStream
;;           InputStreamReader. BufferedReader.)]
;;   (loop [[line & rest] (line-seq stream)]
;;     (when line
;;       (println line)
;;       (recur rest))))

;;(super (forms :ffmpeg-args))
;;=> javax.swing.JTextArea
(defn super [obj] (.getSuperclass (class obj)))

;; (empty-or "" "hoge")
;=> "hoge"
(defmacro empty-or [head & rest]
  (if (empty? rest)
    head
    `(let [x# ~head]
       (if (empty? x#)
         (empty-or ~@rest) x#))))

;; (get-screen-size)
;; "1600x1200"
(defn get-screen-size []
  (let [screen-size (.getScreenSize (Toolkit/getDefaultToolkit))
        x (int (.getWidth screen-size))
        y (int (.getHeight screen-size))]
    (str x "x" y)))

(defn s-to-i [s]
  (try
    (read-string s)
    (catch Exception e 0)))

(defn url-encode [s]
  (some-> s str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

;; (file-name-encode "hogehoge/:*?\"<>|hoge.mkv")
;;=>"hogehogeÅ^ÅFÅñÅHÅhÅÉÅÑÅbhoge.mkv"
(defn file-name-encode [s]
  (replace s #"[\/\:\*\?\"\<\>\|]"
           (zipmap (map str "/:*?\"<>|")
                   (map str "Å^ÅFÅñÅHÅhÅÉÅÑÅb"))))

(defn force-array-map [m keys]
  (apply array-map (flatten (map #(vector %1 (%1 m)) keys))))

(defn if-map [pred f coll] (map #(if (pred %1) (f %1) %1) coll))

(defmacro aif [expr then else]
  `(let [~'it ~expr]
     (if ~'it
       ~then
       ~else)))

;; thanks
;; https://github.com/macourtney/masques/blob/master/src/masques/controller/utils.clj
(defn choose-file
  "Pops up a file chooser and returns the chosen file if the user picks one, otherwise this function returns nil."
  ([owner] (choose-file owner nil))
  ([owner file-selection-mode]
    (let [file-chooser (new JFileChooser)]
      (when file-selection-mode
        (.setFileSelectionMode file-chooser file-selection-mode))
      (when (= JFileChooser/APPROVE_OPTION (.showOpenDialog file-chooser owner))
        (.getSelectedFile file-chooser)))))

(defn choose-directory
  "Pops up a file chooser which only chooses directories. Returns the chosen directory or nil if the user does not select one."
  [owner]
  (choose-file owner JFileChooser/DIRECTORIES_ONLY))


;; good code, thanks
;; https://github.com/clojure/core.incubator/blob/master/src/main/clojure/clojure/core/strint.clj
(defn- silent-read
  "Attempts to clojure.core/read a single form from the provided String, returning
  a vector containing the read form and a String containing the unread remainder
  of the provided String. Returns nil if no valid form can be read from the
  head of the String."
  [s]
  (try
    (let [r (-> s java.io.StringReader. java.io.PushbackReader.)]
      [(read r) (slurp r)])
    (catch Exception e))) ; this indicates an invalid form -- the head of s is just string data

(defn- interpolate
  "Yields a seq of Strings and read forms."
  ([s atom?]
    (lazy-seq
      (if-let [[form rest] (silent-read (subs s (if atom? 2 1)))]
        (cons form (interpolate (if atom? (subs rest 1) rest)))
        (cons (subs s 0 2) (interpolate (subs s 2))))))
  ([^String s]
    (if-let [start (->> ["~{" "~("]
                     (map #(.indexOf s ^String %))
                     (remove #(== -1 %))
                     sort
                     first)]
      (lazy-seq (cons
                  (subs s 0 start)
                  (interpolate (subs s start) (= \{ (.charAt s (inc start))))))
      [s])))

(defmacro <<
  "Accepts one or more strings; emits a `str` invocation that concatenates
the string data and evaluated expressions contained within that argument.
Evaluation is controlled using ~{} and ~() forms. The former is used for
simple value replacement using clojure.core/str; the latter can be used to
embed the results of arbitrary function invocation into the produced string.
Examples:
  user=> (def v 30.5)
  #'user/v
  user=> (<< \"This trial required ~{v}ml of solution.\")
  \"This trial required 30.5ml of solution.\"
  user=> (<< \"There are ~(int v) days in November.\")
  \"There are 30 days in November.\"
  user=> (def m {:a [1 2 3]})
  #'user/m
  user=> (<< \"The total for your order is $~(->> m :a (apply +)).\")
  \"The total for your order is $6.\"
  user=> (<< \"Just split a long interpolated string up into ~(-> m :a (get 0)), \"
           \"~(-> m :a (get 1)), or even ~(-> m :a (get 2)) separate strings \"
           \"if you don't want a << expression to end up being e.g. ~(* 4 (int v)) \"
           \"columns wide.\")
  \"Just split a long interpolated string up into 1, 2, or even 3 separate strings if you don't want a << expression to end up being e.g. 120 columns wide.\"
  
Note that quotes surrounding string literals within ~() forms must be
escaped."
  [& strings]
  `(str ~@(interpolate (apply str strings))))

