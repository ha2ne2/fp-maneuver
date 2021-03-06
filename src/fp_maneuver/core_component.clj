(in-ns 'fp-maneuver.core)


(def evaluated-args 
  (doto (text :multi-line? true :wrap-lines? true :rows 10)
    (.setWrapStyleWord false)))

(def ffmpeg-args-area
  (doto (text :multi-line? true :wrap-lines? true :rows 10
              :listen  [:document eval-ffmpeg-args])
    (.setWrapStyleWord false)))

(def relay-tree-area
  (doto (text :multi-line? true :wrap-lines? true :rows 5
              :editable? false)
    (.setWrapStyleWord false))) ; ← word tanni orikaesi sinai

(def setting-forms
  (into {}
        (map #(vector %1 (text (or (@settings %1) "")))
             setting-items)))

(def history-cmbox
  (combobox :model (map history-to-str-vec @history)
            :listen 
            [:selection
             (fn [e]
               (if (not= -1 (.getSelectedIndex history-cmbox))
                 (set-form-data (@history (.getSelectedIndex history-cmbox)))))]))

(def yp-cmbox 
  (combobox :model [""]))

(def preset-cmbox 
  (combobox :model
            ["ultrafast"
             "superfast"
             "veryfast"
             "faster"
             "fast"
             "medium"
             "slow"
             "slower"
             "veryslow"
             "placebo"]))

(def vcodec-cmbox 
  (combobox :model ["H265+Opus/MKV"
                    "H265+Opus/MKV (nvenc)"
                    "H264+AAC/FLV"
                    "H264+AAC/FLV (nvenc)"]))

(def acodec-cmbox 
  (combobox :model ["Opus (Opus 1.1.4)"
                    "AAC (native 20170425-b4330a0)"
                    "MP3 (LAME 3.99.5)"]))

(def record-chbox (checkbox))

(def aq-strength-spinner
  (spinner
   :model
   (spinner-model 1.0 :by 0.1)))

(def forms
  (merge
   (into {} (map #(vector %1 (text)) text-field-items))
   {:vcodec vcodec-cmbox
    :acodec acodec-cmbox
    :record? record-chbox
    :yp yp-cmbox
    :preset preset-cmbox
    :aq-strength aq-strength-spinner
    :ffmpeg-args ffmpeg-args-area
    }))

(def scroll-chbox (checkbox :text "scroll" :selected? true))

(def output-area (doto
                     (text :multi-line? true
                           :wrap-lines? true
                           :background "BLACK"
                           :foreground "#0F0"
                           :rows 4)
                   (.setWrapStyleWord false)))

(def start-button
  (button
   :text "配信開始"
   :listen
   [:action
    (fn [e]
      ;; ちょっと場当たり的。
      (case (text start-button)
        "配信開始" (start-broadcast)
        "詳細変更" (do (set-channel-info @channel-id (get-form-data))
                       (set-form-state false))))]))

(def stop-button
  (button :text "配信終了"
          :listen [:action stop-broadcast]))

(defn choose-button-gen
  ([target-form] (choose-button-gen target-form nil))
  ([target-form dir?]
   (button :text "..."
           :listen [:action
                    (fn [e]
                      (->> ((if dir? choose-directory choose-file)
                            setting-panel)
                           (.getAbsolutePath)
                           (text! target-form)))])))

(defn device-choose-button-gen [type]
    (button :text "..."
            :listen [:action
                     (fn [e]
                       (reset! settings (get-setting-form-data))
                       (if (= "" (text (setting-forms :ffmpeg-path)))
                         (doto (dialog :type :info :content "先にffmpeg-pathを指定して下さい。")
                           (.setLocationRelativeTo main-window)
                           pack! show!)
                         (try
                           (let [listbox (listbox :model ((get-devices) type))]
                             (.setSelectedIndex listbox 0)
                             (doto (dialog :title (str type "の選択")
                                           :type :question
                                           :content listbox
                                           :success-fn (fn [_] (text!
                                                                (setting-forms type)
                                                                (selection listbox))))
                               (.setLocationRelativeTo main-window)
                               pack! show!))
                           ;; ffmpeg や pactl の実行に失敗した場合。
                           (catch java.io.IOException e
                             (doto (dialog :type :error :content (.getMessage e))
                               (.setLocationRelativeTo main-window)
                               pack! show!)))))]))

(def button-panel
  (horizontal-panel :items [start-button stop-button scroll-chbox]))

(def main-panel
  (border-panel
   :north
   (seesaw.mig/mig-panel
    :constraints ["wrap 2"
                  "[shrink 0]20px[200, grow, fill]"
                  "[shrink 0]5px[]"]
    :items [["履歴:"] [history-cmbox]
            ["peercast host:"] [(forms :host)]
            ["掲載YP:" ]       [(forms :yp)]
            ["チャンネル名:" ] [(forms :cname)]
            ["ジャンル:"]      [(forms :genre)]
            ["詳細:"]          [(forms :desc)]
            ["コメント:"]      [(forms :comment)]
            ["コンタクトURL:"] [(forms :url)]
            ;; ["解像度:"]        [(forms :size)]
            ;; ["FPS:"]           [(forms :fps)]
            ;; ["codec:" ]        [vcodec-cmbox]
            ;; ["プリセット:"]    [preset-cmbox]
            ;; ["video bitrate:" ] [(forms :vbps)]
            ;; ;; ["audio codec:" ] [acodec-cmbox]
            ;; ["audio bitrate:"]  [(forms :abps)]
            [(seesaw.mig/mig-panel
              :constraints["wrap 4"
                           "[shrink 0]10px[]"
                           "[shrink 0]5px[]"]
              :items [["解像度:"] [(forms :size) "grow"]
                      ["FPS:"]    [(forms :fps)  "grow"]
                      ["codec:" ] [vcodec-cmbox  "grow"]
                      ["preset:"] [preset-cmbox  "grow"]
                      ["video kbps:"]  [(forms :vbps) "grow"]
                      ["audio kbps:"]  [(forms :abps) "grow"]
                      ]) "span,grow"]
            ["録画しますか？:"  ]     [record-chbox]

            [""] [button-panel]])
   :center (JScrollPane. output-area)
   ))

(def status-panel
  (seesaw.mig/mig-panel
   :constraints ["wrap 2,fill"
                 "[shrink 0]20px[]"
                 "[shrink 0]"]
   :items [["RELAY TREE:" "top"] [(scrollable relay-tree-area)"grow"]]))
   
(def setting-panel
  (seesaw.mig/mig-panel
    :constraints ["wrap 3"
                  "[shrink 0]20px[200, grow, fill][]"
                  "[shrink 0]5px[]"]
    :items [["ffmepg-path:"]  [(setting-forms :ffmpeg-path)]
            [(choose-button-gen (setting-forms :ffmpeg-path))]
            ["video-device:"] [(setting-forms :video-device)]
            [(device-choose-button-gen :video-device)]
            ["audio-device:"] [(setting-forms :audio-device)]
            [(device-choose-button-gen :audio-device)]
            ["record-path:"]  [(setting-forms :record-path)]
            [(choose-button-gen (setting-forms :record-path) true)]]))

(def encoder-setting-panel
  (seesaw.mig/mig-panel
   :constraints ["wrap 2"
                 "[shrink 0]20px[200, grow, fill]"
                 "[shrink 0]5px[]"]
   :items [["aq-strength:"] [aq-strength-spinner]
           ["ffmpeg-args:"] [(scrollable (forms :ffmpeg-args))]
           ["evaluated:"] [(scrollable evaluated-args)]
           [""] [(horizontal-panel
                  :items [(button :text "設定ファイルを再読込"
                                  :listen [:action
                                           (fn [e]
                                             (my-set :ffmpeg-args
                                                     (if (not= -1 (.getSelectedIndex history-cmbox))
                                                       (:ffmpeg-args (@history
                                                                      (.getSelectedIndex history-cmbox)))
                                                       ffmpeg-args)))])
                          (button :text "初期値を設定"
                                  :listen [:action
                                           (fn [e] (my-set :ffmpeg-args ffmpeg-args))])])]]))
(def main-window
  (doto (frame :title software-name :on-close (if debug :hide :exit)
               :content (tabbed-panel
                         :placement :top
                         :overflow  :wrap
                         :tabs [{:title "配信" :content main-panel}
                                {:title "配信状況" :content status-panel}
                                {:title "基本設定" :content setting-panel}
                                {:title "エンコ設定" :content encoder-setting-panel}
                                ]))
    (.setIconImages
     (map (comp #(.getImage %) seesaw.icon/icon clojure.java.io/resource)
          ["16.png" "32.png" "48.png" "64.png" "128.png" "256.png" "512.png"]))))



;; 配信中にクリックで詳細を編集可能に。
(mapc #(listen (forms %)
               :mouse-clicked
               (fn [e]
                 (when (not (and (config start-button :enabled?)
                                 (= (config start-button :text) "配信開始")))
                   (mapc (fn [key] (config! (forms key) :enabled? true))
                         [:genre :desc :comment :url])
                   (request-focus! (.getSource e))
                   (config! start-button
                            :text "詳細変更"
                            :enabled? true))))
      [:genre :desc :comment :url])




;; 設定項目に変更があった時インタラクティブにeval

;; [:host :cname :genre :desc :comment :url :size :fps :vbps :abps :ffmpeg-args]
;; ドキュメント変更時に。
(mapc #(listen (forms %) :document eval-ffmpeg-args)
      (conj text-field-items :ffmpeg-args))

;; (:acodec :record? :yp :preset :vcodec)
;; クリック時に。
(mapc #(listen (forms %) :action eval-ffmpeg-args)
      (keys
       (reduce (fn [acc x] (dissoc acc x))
               forms
               (conj text-field-items :ffmpeg-args :aq-strength))))

(listen (forms :aq-strength) :change eval-ffmpeg-args)

(listen (forms :host) :document update-host)

