FPManeuver v1.2.1

配信開始ボタン押すだけで配信を始められるツールです。
ピアキャストからチャンネルを立てる必要がありません。
予定地さんが開発しているPeerCast YTの機能を使っています。
H265/MKV配信にも対応！

必要ソフト
・PeerCast YT <https://github.com/plonk/peercast-yt>
　ハツネツサーバを使う場合は不要です。
・FFmpeg      <https://ffmpeg.org/>
　内蔵版をDLした場合は追加DL不要です。
・Java        <https://www.java.com/ja/>
　必要です。

使い方
fp-maneuver.jarをダブルクリックで起動。
設定タブを開いて基本設定をする。
host、チャンネル名、ジャンル等を入力し、配信開始ボタンを押す。
分からなかったり動かなかったりしたら聞いて下さい。

peercast hostの所にはPeerCast YTの動いているホストURLを入れて下さい。
現在http://ha2ne2.tokyo:7144を開放しているので使ってもらって構いません。
（YPにはTPを利用させて貰っていますので、ジャンル名の前にtpと付けて下さい。
　例えばジャンルが「ゲーム」だった場合は「tpゲーム」として下さい。）
分からない事があったら聞いて下さい。

■■■■　連絡先　■■■■

ハツネツエリア - したらば掲示板
http://jbbs.shitaraba.net/internet/17144/

ハツネツ - twitter
http://twitter.com/hatsunetsu7


■■■■ Release NOTE ■■■■

v1.2.1 Release 2017/05/22
・aq-strengthの値が常に0として扱われていたのを修正

v1.2.0 Release 2017/05/17
・エンコ設定タブを付けて、FFmpegオプションを手動で変更できるようにした
・Linuxで配信出来るようにした
　video-deviceにはx11grab、auido-deviceには
  $ pactl list sources | grep -E '(Name|名前):'
  で取得できるデバイス名を指定して下さい。
・aq-strengthの設定フォームを付けた
　フレーム内でのビット再配分の強弱を設定する値です。デフォルトは1。
　アニメ系は低く(0.8程度)、実写系、FPS、シューティングゲーム等は
　高く(1.3程度)設定すると良いようです。プログラミング配信は高い方
　が画質が良かったです。
・H264/FLV配信にメタビットレート値を載せるようにした
・履歴が重複して保存される場合があったのを修正

v1.1.0 Release 2017/04/30
・設定タブを付けた
・H264/FLVで配信出来るようにした
・オーディオをMP3,AAC,Opusから選べるようにした
・エンコーダの処理の重さを選べるようにした。画質と重さはトレードオフです

v1.0.0 Release 2017/04/26
初期バージョン

