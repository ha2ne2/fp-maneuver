(ns fp-maneuver.core
  (:gen-class)
  (:import (java.net URLEncoder URLDecoder)
           (java.io PrintStream ByteArrayOutputStream
                    InputStreamReader BufferedReader
                    OutputStreamWriter BufferedWriter
                    FileNotFoundException
                    File)
           java.util.Date
           java.text.SimpleDateFormat
           java.lang.ProcessBuilder
           java.awt.Toolkit
           java.lang.Math
           (javax.swing JFileChooser JScrollPane))
  (:use seesaw.core)
  (:require seesaw.mig
            seesaw.chooser
            clojure.pprint
            [clojure.string :refer [starts-with? replace]]
            [environ.core :refer [env]]))

(native!)

(load "core_util")

(def debug (env :dev))

(declare 
 forms setting-forms history-cmbox
 preset-cmbox vcodec-cmbox acodec-cmbox
 record-chbox output-area
 start-button stop-button button-panel
 main-panel setting-panel main-window
 history settings aq-strength-spinner
 scroll-chbox evaluated-args)

;; uberjarにするとpropertyが脱落するのでコンパイルタイムに定数化しておく
(defmacro get-version []
  (System/getProperty "fp-maneuver.version"))

(def software-name (str "FPManeuver v" (get-version)))

(def windows? (starts-with? (System/getProperty "os.name") "Windows"))

(def ffmpeg-process (atom nil))
(def ffmpeg-writer (atom nil))

(def items [:host :cname :genre :desc :comment :url
            :preset :size :fps :vcodec :vbps :acodec :abps
            :record? :aq-strength :ffmpeg-args])

(def s-to-i? #{:fps :vbps :abps})

(def text-field-items
  [:host :cname :genre :desc :comment :url :size :fps :vbps :abps])

(def setting-items
  [:ffmpeg-path :video-device :audio-device :record-path])

(def ffmpeg-args
  (if windows?
    (str
     "~{ffmpeg-path} -re -rtbufsize 50MB "
     "-f dshow -framerate ~{fps} -video_size ~{size} "
     "-i video=\"~{video-device}\":audio=\"~{audio-device}\" -flags +global_header "
     "-threads 0 -vsync ~{vsync} "
     "-vcodec ~{vcodec} \"bitrate=~{vbps}:vbv-maxrate=~{vbps}:vbv-bufsize=~(* vbps 2):"
     "min-keyint=~{fps}:keyint=~(* fps 10):aq-mode=2:aq-strength=~{aq-strength}\" "
     "-preset ~{preset} "
     "-acodec ~{acodec} -b:a ~{abps}k -f ~{file-format}")
    (str
     "~{ffmpeg-path} -re -rtbufsize 50MB "
     "-f ~{video-device} -framerate ~{fps} -video_size ~{screen-size} -i :0.0 "
     "-f pulse -ac 2 -i ~{audio-device} "
     "-threads 0 -vsync ~{vsync} -pix_fmt yuv420p "
     "-vcodec ~{vcodec} \"bitrate=~{vbps}:vbv-maxrate=~{vbps}:vbv-bufsize=~(* vbps 2):"
     "min-keyint=~{fps}:keyint=~(* fps 10):aq-mode=2:aq-strength=~{aq-strength}\" "
     "-s ~{size} -preset ~{preset} "
     "-acodec ~{acodec} -b:a ~{abps}k -f ~{file-format}")
    ))

(defn my-get [key]
  ((comp (if (s-to-i? key) s-to-i identity)
         (condp get (super (forms key))
           #{javax.swing.JTextArea javax.swing.JTextField} text
           #{javax.swing.JCheckBox javax.swing.JComboBox} selection
           ;; JSpinner は+0.1とかすると誤差が出る
           #{javax.swing.JSpinner} (comp (fn [n] (/ (int (* (+ 0.05 n) 10)) 10.0)) selection)))
   (forms key)))

(defn get-form-data []
  (into {} (map #(vector %1 (my-get %1))) (keys forms)))

(defn my-set [key val]
  ((condp get (super (forms key))
     #{javax.swing.JTextArea javax.swing.JTextField} text!
     #{javax.swing.JCheckBox javax.swing.JComboBox javax.swing.JSpinner} selection!)
   (forms key)
   (if (and (= key :ffmpeg-args) (empty? val))
     ffmpeg-args val)))

(defn set-form-data [chinfo]
  (reduce-kv (fn [acc key val] (my-set key val) nil)
             nil (force-array-map chinfo items)))

(defn get-setting-form-data []
  (into {} (map #(vector %1 (text (setting-forms %1))) setting-items)))

(defn set-form-state [state]
  (config! (concat [history-cmbox start-button evaluated-args]
                   (vals forms) (vals setting-forms))
           :enabled? state)
  (config! stop-button :enabled? (not state)))

(defn history-to-str-vec [history]
  (map #(reduce str (interpose " " ((juxt :cname :genre :desc) %1))) history))

(defn read-history []
  (try (let [s (slurp "history.clj")
             hist (read-string s)]
         (if (instance? clojure.lang.PersistentVector hist)
           hist
           (read-string (str "[" s "]")))) ;; 後方互換性の為
       (catch Exception e
         [(force-array-map 
           {:host "http://ha2ne2.tokyo:7144"
            :size "800x600"
            :fps 20
            :preset "medium"
            :vcodec "H264/FLV (x264 20170123-90a61ec)"
            :vbps 500
            :acodec "AAC (native 20170425-b4330a0)"
            :abps 96
            :record? true
            :aq-strength 1.0}
           items)])))

(defn write-history []
  (let [chinfo (get-form-data)
        chinfo (force-array-map 
                (if (= (:ffmpeg-args chinfo) ffmpeg-args)
                  (dissoc chinfo :ffmpeg-args) chinfo)
                items)
        tmp ((juxt :cname :genre :desc) chinfo)
        new-history (into [chinfo] (filter #(not= tmp ((juxt :cname :genre :desc) %1)) @history))]
    (spit
     "history.clj"
     (str (clojure.pprint/write new-history :stream nil) "\r\n\r\n"))
    (reset! history new-history)
    (.removeAllItems history-cmbox)
    (dorun (map #(.addItem history-cmbox %1) (history-to-str-vec @history)))))

(def history (atom (read-history)))

(defn read-settings []
  (try (read-string (slurp "settings.clj"))
       (catch Exception e
         (if windows?
           {}
           {:video-device "x11grab"}))))

(defn write-settings [s]
  (spit
   "settings.clj"
   (str (clojure.pprint/write
         (force-array-map s setting-items)
         :stream nil)
        "\r\n\r\n")))

(def settings (atom (read-settings)))

(defn get-devices []
  (if (and (= "" (text (setting-forms :ffmpeg-path))))
    (throw (Exception. "ffmpeg-pathを指定して下さい。"))
    (do
      (let [process (.exec (Runtime/getRuntime)
                           (str (text (setting-forms :ffmpeg-path))
                                " -list_devices true -f dshow -i dummy"))
            error (BufferedReader.
                   (InputStreamReader. (.getErrorStream process) "UTF-8"))]
        (loop [[x & xs] (line-seq error)
               acc {:video-device [] :audio-device []}
               type :video-device]
          (if x
            (if-let [result (re-find #"^.*]  \"(.*)\"" x)]
              (recur xs
                     (update acc type conj (second result))
                     type)
              (recur xs acc (if (and (= type :video-device)
                                     (re-find #"DirectShow audio devices" x))
                              :audio-device type)))
            acc))))))

;(gen-command (get-form-data))

(defn gen-args [chinfo ffmpeg-args]
  (eval
   `(let [{:keys [~'preset ~'fps ~'size ~'vcodec ~'vbps ~'acodec ~'abps ~'aq-strength]} ~chinfo
          [~'fps ~'vbps ~'abps] (map #(if (instance? java.lang.String %1) (read-string %1) %1)
                                     [~'fps ~'vbps ~'abps]) ;; 後方互換
          ~'screen-size ~(get-screen-size)
          {:keys [~'ffmpeg-path ~'video-device ~'audio-device]} @settings
          ~'file-format (if (starts-with? ~'vcodec "H265/MKV")
                          "matroska" "flv")
          ~'vcodec (if (starts-with? ~'vcodec "H265/MKV")
                     "libx265 -x265-params"
                     "libx264 -x264-params")
          ~'acodec (cond (starts-with? ~'acodec "Opus") "libopus"
                         (starts-with? ~'acodec "AAC") "aac"
                         :else "libmp3lame")
          ~'vsync (if (< ~'fps 50) "passthrough" "-1")]
      (str ~@(interpolate ffmpeg-args)))))

;; (gen-url (get-form-data))
;; ;=>"http://ha2ne2.tokyo:8144/?name=テストちゃんねる&genre=プログラミング
;;      &desc=テスト&comment=&url=http://yahoo.co.jp&type=FLV&bitrate=364"
(defn gen-url [{:keys [host cname genre desc comment url vcodec vbps abps]}]
  (let [[vbps abps] (if-map #(instance? java.lang.String %1) read-string
                            [vbps abps]) ;; 後方互換性 前はhistoryにstringとして保存していた
        [cname genre desc comment url] (map url-encode [cname genre desc comment url])
        type (if (starts-with? vcodec "H265/MKV") "MKV" "FLV")
        bitrate (+ vbps abps)]
    (<< "~{host}/?name=~{cname}&genre=~{genre}&desc=~{desc}&comment=~{comment}"
        "&url=~{url}&type=~{type}&bitrate=~{bitrate}")))

(defn gen-command [{:keys [genre desc vcodec record?] :as chinfo}]
  (let [{:keys [ffmpeg-path record-path]} @settings
        record-path (clojure.string/replace record-path #"\\" "/")
        date  (.format (SimpleDateFormat. "yyMMdd_HHmmss") (Date.))
        args  (gen-args chinfo (my-get :ffmpeg-args))
        url   (gen-url chinfo)
        type  (if (starts-with? vcodec "H265/MKV") "matroska" "flv")
        ext   (if (starts-with? vcodec "H265/MKV") "mkv" "flv")
        shell (if windows? ["cmd" "/c"] ["/bin/sh" "-c"])
        genre (file-name-encode genre)
        desc  (file-name-encode desc)
        ]
    (conj shell
          (if record?
            (<< "~{args} - | ~{ffmpeg-path} -i - -c copy -f ~{type} \"~{url}\" "
                "-c copy -f ~{type} \"~{record-path}/~{date}_~{genre}_~{desc}.~{ext}\" -nostats")
            (<< "~{args} \"~{url}\"")))))

;; (format-ffmpeg-output "frame= 7710 fps= 30 q=-0.0 q=29.0 size=    6236kB time=00:04:16.48 bitrate= 199.2kbits/s speed=0.998x")
;;=> "[00:04:16] 6.0MB 30fps 199.2kbps 0.998x"
(defn format-ffmpeg-output [line]
  (if-let [result (re-find #"frame=.*?(\d+).*?fps=.*?(\d+).*?size=.*?(\d+)kB time=(\d\d:\d\d:\d\d).*bitrate= (.*)kbits/s speed=(.*?x)" line)]
    (let [[frame fps size time bitrate speed] (rest result)]
      (format "[%s] %.2fMB %sfps %skbps %s"
              time
              (-> (/ (read-string size) 1024.0)
                  (* 100) int (/ 100.0))
              fps bitrate speed)) 
    line))

(defn start-broadcast [_]
  (reset! settings (get-setting-form-data))
  (cond ((some-fn (comp empty? @settings)) :ffmpeg-path :video-device :audio-device)
        (doto (dialog :type :info :content "未記入の設定があります。\n基本設定タブから設定をして下さい。")
          (.setLocationRelativeTo main-window)
          pack! show!)

        (and (selection record-chbox) (empty? (@settings :record-path)))
        (doto (dialog :type :info :content "録画をする場合は基本設定タブから録画パスを設定して下さい。")
          (.setLocationRelativeTo main-window)
          pack! show!)

        (empty? (my-get :cname))
        (doto (dialog :type :info :content "チャンネル名を設定して下さい。")
          (.setLocationRelativeTo main-window)
          pack! show!)

        :else
        (let [command (gen-command (get-form-data))]
          (invoke-later
           (.append output-area (str "> " (reduce str command) "\n\n"))
           (scroll! output-area :to :bottom))
          (write-history)
          (write-settings @settings)
          (set-form-state false)

          ;; (let [stream
          ;;       (-> (ProcessBuilder. ["cmd" "/c" "dir"])
          ;;           .start .getInputStream
          ;;           InputStreamReader. BufferedReader.)]
          ;;   (loop [[line & rest] (line-seq stream)]
          ;;     (when line
          ;;       (println line)
          ;;       (recur rest))))

          (reset! ffmpeg-process (.start (ProcessBuilder. command)))
          (let [error (BufferedReader. (InputStreamReader. (.getErrorStream @ffmpeg-process) "UTF-8"))]
            (reset! ffmpeg-writer
                    (BufferedWriter. (OutputStreamWriter. (.getOutputStream @ffmpeg-process) "UTF-8")))
            (future (loop [s (.readLine error)]
                      (if s
                        (do (invoke-later
                             (when (selection scroll-chbox)
                               (scroll! output-area :to :bottom)))
                            (.append output-area (str "\n" (format-ffmpeg-output s)))
                            (recur (.readLine error)))
                        (do
                          (.beep (Toolkit/getDefaultToolkit))
                          (doto (dialog :type :info :content "ffmpegが終了しました")
                            (.setLocationRelativeTo main-window)
                            pack! show!)
                          (set-form-state true))
                          )))))))

(defn choose-button-gen
  ([target-form] (choose-button-gen target-form nil))
  ([target-form dir?]
   (button :text "..."
           :listen [:action
                    (fn [e]
                      (->> ((if dir? choose-directory choose-file)
                            setting-panel)
                           (.getAbsolutePath)
                           (text! target-form))
                      )])))

(defn device-choose-button-gen [type]
    (button :text "..."
            :listen [:action
                     (fn [e]
                       (reset! settings (get-setting-form-data))
                       (if (= "" (text (setting-forms :ffmpeg-path)))
                         (doto (dialog :type :info :content "先にffmpeg-pathを指定して下さい。")
                           (.setLocationRelativeTo main-window)
                           pack! show!)
                         (let [listbox (listbox :model ((get-devices) type))]
                           (.setSelectedIndex listbox 0)
                           (doto (dialog :title (str type "の選択")
                                         :type :question
                                         :content listbox
                                         :success-fn (fn [_] (text!
                                                              (setting-forms type)
                                                              (selection listbox))))
                             (.setLocationRelativeTo main-window)
                             pack! show!))))]))

(defn eval-ffmpeg-args [_]
  (text! evaluated-args
         (try (gen-args (get-form-data) (my-get :ffmpeg-args))
              (catch Exception e (str "SYNTAX ERROR:\n" e)))))

(load "core_component")

(defn -main [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(.destroy @ffmpeg-process)))
  (if (< 0 (count @history)) (set-form-data (@history 0)))
  (set-form-state true)
  (-> main-window pack! show!))

(when debug (-main))




  


