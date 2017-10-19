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
            [clojure.xml :as xml]
            [clojure.string :refer [starts-with? replace]]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clj-http.client :as client]))

(native!)

(load "core_util")

(def debug (env :dev))

(declare 
 forms setting-forms history-cmbox yp-cmbox
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

(def peca-version (atom nil))
(def yellow-pages (atom nil))
(def channel-id (atom nil))
(def ffmpeg-process (atom nil))
(def ffmpeg-writer (atom nil))
(def localhost "http://localhost")
(def http-push-server (atom nil))
;; (+ (rand-int 16384) 49152)
;; = 49152～65535

(def items [:host :cname :genre :desc :comment :url
            :preset :size :fps :vcodec :vbps :acodec :abps :yp
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
     "-vcodec ~{vcodec} "
     "~(if nvenc? "
     "(<< \"-b:v ~{vbps}k -maxrate ~{vbps}k\") "
     "(<< \"bitrate=~{vbps}:vbv-maxrate=~{vbps}:vbv-bufsize=~(* vbps 2):min-keyint=~{fps}:keyint=~(* fps 10):aq-mode=2:aq-strength=~{aq-strength}\")) "
     ;; \"bitrate=~{vbps}:vbv-maxrate=~{vbps}:vbv-bufsize=~(* vbps 2):"
     ;; "min-keyint=~{fps}:keyint=~(* fps 10):aq-mode=2:aq-strength=~{aq-strength}\" "
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

; into {} map #() ってやつよく書くのでなんかutility関数作りたい。
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
   ;; ここにあるのちょっと変だけど妥協
  (config! start-button :text "配信開始")
  (config! stop-button :enabled? (not state)))

(defn history-to-str-vec [history]
  (reduce str (interpose " " ((juxt :cname :genre :desc) history))))

(defn read-history []
  (try (let [s (slurp "history.clj")
             hist (read-string s)]
         (if (instance? clojure.lang.PersistentVector hist)
           hist
           (read-string (str "[" s "]")))) ;; 後方互換性の為
       (catch Exception e
         [(force-array-map 
           {:host "http://localhost:7144"
            :size "800x600"
            :fps 30
            :preset "medium"
            :vcodec "H264+AAC/FLV"
            :vbps 500
            ;:acodec "AAC (native 20170425-b4330a0)"
            :yp "TP"
            :abps 96
            :record? true
            :aq-strength 1.0}
           items)])))

(defn write-history []
  (let [chinfo (get-form-data)
        chinfo (force-array-map 
                (if (= (chinfo :ffmpeg-args) ffmpeg-args)
                  (dissoc chinfo :ffmpeg-args)
                  chinfo)
                items)
        tmp ((juxt :cname :genre :desc) chinfo)
        new-history (into [chinfo] (filter #(not= tmp ((juxt :cname :genre :desc) %1)) @history))]
    (spit
     "history.clj"
     (str (clojure.pprint/write new-history :stream nil) "\r\n\r\n"))
    (reset! history new-history)
    (.removeAllItems history-cmbox)
    (dorun (map #(.addItem history-cmbox %1) (map history-to-str-vec @history)))))

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

(defn pulse-audio-devices []
  (let [process (.exec (Runtime/getRuntime)
                       "pactl list short sources")
        input (BufferedReader.
               (InputStreamReader. (.getInputStream process) "UTF-8"))]
    (loop [[x & xs] (line-seq input)
           acc []]
      (if x
        (if-let [result (re-find #"^\d+\s+(\S+)" x)]
          (recur xs (conj acc (second result)))
          (recur xs acc))
        acc))))

(defn get-devices []
  (if (and (= "" (text (setting-forms :ffmpeg-path))))
    (throw (Exception. "ffmpeg-pathを指定して下さい。"))
    (if windows?
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
            acc)))
      {:video-device ["x11grab"] :audio-device (pulse-audio-devices)})))

;(gen-command (get-form-data))

(defn gen-args [chinfo ffmpeg-args]
  (eval
   `(do
      (use 'fp-maneuver.core)
      (let [{:keys [~'preset ~'fps ~'size ~'vcodec ~'vbps ~'acodec ~'abps ~'aq-strength]} ~chinfo
            [~'fps ~'vbps ~'abps] (map #(if (instance? java.lang.String %1) (read-string %1) %1)
                                       [~'fps ~'vbps ~'abps]) ;; 後方互換
            ~'screen-size ~(get-screen-size)
            {:keys [~'ffmpeg-path ~'video-device ~'audio-device]} @settings
            ~'file-format (if (starts-with? ~'vcodec "H265")
                            "matroska" "flv")
            ~'nvenc? (not= (last ~'vcodec) \V)
            ~'acodec (if (starts-with? ~'vcodec "H265") "libopus"
                         "aac")
            ~'vcodec (case ~'vcodec
                       "H265+Opus/MKV"         "libx265 -x265-params"
                       "H265+Opus/MKV (nvenc)" "hevc_nvenc"
                       "H264+AAC/FLV"          "libx264 -x264-params"
                       "H264+AAC/FLV (nvenc)"  "h264_nvenc")
            ~'vsync (if (< ~'fps 50) "passthrough" "-1")]
        (str ~@(interpolate ffmpeg-args))))))

;; (gen-url (get-form-data))
;; ;=>"http://ha2ne2.tokyo:8144/?name=テストちゃんねる&genre=プログラミング
;;      &desc=テスト&comment=&url=http://yahoo.co.jp&type=FLV&bitrate=364"
(defn gen-url [{:keys [host cname genre desc comment url vcodec vbps abps]}]
  (let [[vbps abps] (if-map #(instance? java.lang.String %1) read-string
                            [vbps abps]) ;; 後方互換性 前はhistoryにstringとして保存していた
        [cname genre desc comment url] (map url-encode [cname genre desc comment url])
        type (if (starts-with? vcodec "H265") "MKV" "FLV")
        bitrate (+ vbps abps)]
    (<< "~{host}/?name=~{cname}&genre=~{genre}&desc=~{desc}&comment=~{comment}"
        "&url=~{url}&type=~{type}&bitrate=~{bitrate}")))

(defn gen-command [{:keys [genre desc vcodec record?] :as chinfo}]
  (let [{:keys [ffmpeg-path record-path]} @settings
        record-path (clojure.string/replace record-path #"\\" "/")
        date  (.format (SimpleDateFormat. "yyMMdd_HHmmss") (Date.))
        args  (gen-args chinfo (my-get :ffmpeg-args))
        url   (if (= "ST" @peca-version)
                @http-push-server
                (gen-url chinfo))
        type  (if (starts-with? vcodec "H265") "matroska" "flv")
        ext   (if (starts-with? vcodec "H265") "mkv" "flv")
        shell (if windows? ["cmd" "/c"] ["/bin/sh" "-c"])
        genre (file-name-encode genre)
        desc  (file-name-encode desc)
        ]
    (conj shell
          (if record?
            (<< "~{args} - | ~{ffmpeg-path} -i - -c copy -f ~{type} \"~{url}\" "
                "-c copy -f ~{type} \"~{record-path}/~{date}_~{genre}_~{desc}.~{ext}\" -nostats")
            (<< "~{args} \"~{url}\"")))))

(defn eval-ffmpeg-args [_]
  (text! evaluated-args
         (try (gen-args (get-form-data) (my-get :ffmpeg-args))
              (catch Exception e (str "SYNTAX ERROR:\n" e)))))


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

;; プロセス実行サンプル
;; (let [stream
;;       (-> (ProcessBuilder. ["cmd" "/c" "dir"])
;;           .start .getInputStream
;;           InputStreamReader. BufferedReader.)]
;;   (loop [[line & rest] (line-seq stream)]
;;     (when line
;;       (println line)
;;       (recur rest))))

(defn get-writer [process]
  (BufferedWriter.
   (OutputStreamWriter. (.getOutputStream process) "UTF-8")))

(defn get-error-reader [process]
  (BufferedReader.
   (InputStreamReader. (.getErrorStream process) "UTF-8")))

(defn show-dialog [message type]
  (doto (dialog :type type :content message)
    (.setLocationRelativeTo main-window)
    pack! show!))

(defn valid-form? []
  (cond
    ((some-fn (comp empty? @settings)) :ffmpeg-path :video-device :audio-device)
    (do (show-dialog "初回起動時は基本設定タブから\nビデオデバイスとオーディオデバイスを設定して下さい。" :info) false)

    (and (selection record-chbox) (empty? (@settings :record-path)))
    (do (show-dialog "録画をする場合は基本設定タブから録画パスを設定して下さい。" :info) false)

    (empty? (my-get :cname))
    (do (show-dialog "チャンネル名を設定して下さい。" :info) false)

    :else true))

;; (post "hoge" "hoge2")
;; (client/post "http://localhost:7144/api/1"
;;  {:body "{\"jsonrpc\":\"2.0\",\"id\":254,\"method\":\"hoge\",\"params\":\"hoge2\"}",
;;   :headers {"X-Requested-With" "XMLRequest"}, :content-type :json, :accept :json})
(defn post [method params]
  (client/post (str (my-get :host) "/api/1") ; hostが/で終わっていてもいなくても通る
               {:body (json/write-str
                       ;; paramsがある時は追加
                       ((if params #(assoc %1 :params params) identity)
                         {:jsonrpc "2.0"
                          :id (rand-int 1000)
                          :method method}))
                :headers {"X-Requested-With" "XMLRequest"}
                :content-type :json
                :accept :json}))

;; (get-channel-id-from-viewxml (get-form-data))
;; => "E71C9E8787887CC1F99E3F0C78272942"
(defn get-channel-id-from-viewxml [chinfo]
  (some (fn [tag] (when (= :channel (:tag tag))
                    (let [[id & info] (gets (:attrs tag) [:id :name :genre :desc])]
                      (when (= info (gets chinfo [:cname :genre :desc]))
                        id))))
        (xml-seq (xml/parse (str (:host chinfo) "/admin?cmd=viewxml")))))


;; (get-yellow-pages) ;=> {"TP" 1119160141, "SP" 903724922}
;; 例外対策いるかなぁまぁいいか
(defn get-yellow-pages []
  (let [raw (post "getYellowPages" nil)
        result ((json/read-str (raw :body)) "result")]
    (reduce (fn [acc yp] (assoc acc (yp "name") (yp "yellowPageId")))
          {"掲載しない" nil} result)))


;; (get-peca-version) ;=> "ST" || "YT" || "Unknown host"
(defn get-peca-version []
  (try
    (let [raw (post "getVersionInfo" nil)
          result (((json/read-str (raw :body)) "result") "agentName")]
      (if (starts-with? result "PeerCastStation")
        "ST"
        "YT"))
    (catch clojure.lang.ExceptionInfo e
       (if (= ((ex-data e) :reason-phrase) "Not Found")
         "Unknown"
         "YT"))
    (catch Exception e "Unknown")))

;; (create-channel (get-form-data))
;; return: channelID
(defn create-channel [chinfo]
  (let [raw (post "broadcastChannel"
                   {:yellowPageId (@yellow-pages (my-get :yp))
                    :sourceUri @http-push-server
                    :sourceStream "HTTP Push Source"
                    :contentReader (if (starts-with? (chinfo :vcodec) "H265")
                                     "Matroska (MKV or WebM)" "Flash Video (FLV)")
                    :info (assoc
                           (zipmap [:name :genre :desc :comment :url]
                                   (gets chinfo [:cname :genre :desc :comment :url]))
                           :bitrate (+ (my-get :vbps) (my-get :abps)))
                    :track {} ;; <- ないと弾かれる
                    })
        result ((json/read-str (raw :body)) "result")]
    result))

;; STOP成功時: null, 失敗時: Channel not found || Invalid channelId
(defn stop-channel [channel-id]
  (let [raw (post "stopChannel" {:channelId channel-id})]
    raw))

(defn set-channel-info [channel-id chinfo]
  (let [raw (post "setChannelInfo"
                  {:channelId channel-id
                   :info (zipmap [:name :genre :desc :comment :url]
                                 (gets chinfo [:cname :genre :desc :comment :url]))
                   :track {:url "" :name "" :creator "" :album "" :genre ""}
                   })]
    (println raw)))

(def prev-start (atom (System/currentTimeMillis)))

;; ピアキャスと通信して、STならYP一覧を取得し、GUI更新。
;; host欄に変更がある度インタラクティブにチェックするため
;; チェックは別スレッドでやる。入力途中、例えばhttp://localhまで
;; 打った時に発生する通信は失敗してタイムアウト待ちになるが、その結果は
;; http://localhost:7144まで入力した時の結果が帰ってくるよりも
;; あとに返ってくる場合がある。なので実行開始時刻で比較し適宜破棄。
(defn update-host [_]
  (when (not (empty? (my-get :host)))
    (let [current-start (System/currentTimeMillis)]
      (future
        (let [pecav (get-peca-version)
              yps (case pecav
                    "ST" (get-yellow-pages)
                    "YT" {"既定(YT)" 1}
                    "Unknown" {"Unknown host" -1})]
          ;; 実行開始時刻が最新の場合のみ反映
          ;;（過去の実行結果が、遅れて到着した場合は無視する）
          (when (> current-start @prev-start)
            (reset! prev-start current-start)
            (when (not= yps @yellow-pages)
              (invoke-later
               (.removeAllItems yp-cmbox)
               (mapc #(.addItem yp-cmbox %) (sort (keys yps))))
              (reset! yellow-pages yps))
            (when (not= pecav @peca-version)
              (reset! peca-version pecav))))))))

(defn launch-ffmpeg-reader [reader]
  (future
    (loop [[line & rest] (line-seq reader)]
      (when line
        (invoke-later
         (when (selection scroll-chbox)
           (scroll! output-area :to :bottom))
         (.append output-area (str "\n" (format-ffmpeg-output line))))
        (recur rest)))
    (.beep (Toolkit/getDefaultToolkit))
    (show-dialog "ffmpegが終了しました" :info)
    (set-form-state true)))

(defn start-broadcast []
  (reset! settings (get-setting-form-data))
  (when (valid-form?)
    (when (= @peca-version "ST")
      (reset! http-push-server (str localhost ":" (+ (rand-int 16384) 49152))))
    (let [command (gen-command (get-form-data))]
      (invoke-later
       (.append output-area (str "> " (reduce str command) "\n\n"))
       (scroll! output-area :to :bottom))
      (write-history)
      (write-settings @settings)
      (set-form-state false)
      (when (= @peca-version "ST")
        (reset! channel-id (create-channel (get-form-data))))
      (reset! ffmpeg-process (.start (ProcessBuilder. command)))
      (reset! ffmpeg-writer (get-writer @ffmpeg-process))
      (launch-ffmpeg-reader (get-error-reader @ffmpeg-process))
      ;; YTの時は10秒後にチャンネルIDを取得する。
      (when (= @peca-version "YT")
        (timer (fn [e]
                 (reset! channel-id (get-channel-id-from-viewxml (get-form-data))))
               :initial-delay 10000
               :repeats? false)))))

(defn stop-broadcast [_]
  (when (= @peca-version "ST")
    (stop-channel @channel-id))
  (set-form-state true)
  (.write @ffmpeg-writer "q")
  (.flush @ffmpeg-writer))

(load "core_component")

(defn -main [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(.destroy @ffmpeg-process)))
  (set-form-state true)
  (set-form-data (@history 0))
  (-> main-window pack! show!))

(when debug (-main))


