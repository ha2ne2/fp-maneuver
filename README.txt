
FPManeuver v1.4.0

配信開始ボタン押すだけで配信が始められるツールです。
ピアキャストからチャンネルを立てる必要がありません。
PeerCast Station と PeerCast YT に対応。
Windows、Mac、Linuxで動きます。
H265/MKV配信も出来ます。

必要ソフト
・Java <https://www.java.com/ja/>
・SCFF(画面取り込みソフト) <https://github.com/Alalf/SCFF-DirectShow-Filter>

使い方
fp-maneuver.jarをダブルクリックで起動します。
初回起動時は基本設定タブを開いてオーディオデバイスとビデオデバイスを指定して下さい。
あとはチャンネル名などを入力して配信開始を押して下さい。
分からない事があったら聞いて下さい。

■■■■　連絡先　■■■■

ハツネツエリア - したらば掲示板
http://jbbs.shitaraba.net/computer/44643/

ハツネツ - twitter
http://twitter.com/hatsunetsu7


■■■■ Release NOTE ■■■■

v1.4.0 Release 2017/10/19
・配信中に詳細の変更が出来るようになりました。
　配信中に変更したい項目をクリックすると編集可能になります。
　編集が終わったら、詳細変更ボタンをクリックして下さい。

v1.3.1 Release 2017/08/12
・NVENCハードウェアエンコーダを使ったH264/H265配信を出来るようにした。
　H264はGeForce600番台以降、H265はGeForce900番台以降で使えます。
　GPUでエンコードをする事でCPU使用率が劇的に下がります。
　画質はCPUでエンコードするより多少下がりますが、
　その分ビットレートを上げれば補えます。
　presetはfast/medium/slowの3つが選べます。
・ffmpeg-argsを手動変更した時にバグるバグを修正

v1.3.0 Release 2017/08/11
・PeerCast Stationに対応した
・Linuxでビデオ/音声デバイスの指定がGUIから出来るようになりました(by Yoteichi)

v1.2.0 Release 2017/05/17
・エンコ設定タブからFFmpegオプションを手動で変更できるようにした
・Linuxで配信出来るようにした
　video-deviceにはx11grab、auido-deviceには
  $ pactl list sources | grep -E '(Name|名前):'
  で取得できるデバイス名を指定して下さい。
・aq-strengthの設定フォームを付けた
　フレーム内でのビット再配分の強弱を設定する値です。デフォルトは1。
　アニメ系は低く(0.8程度)、実写系、FPS、シューティングゲーム等は
　高く(1.3程度)設定すると良いようです。
　プログラミング配信は高い方が画質が良かったです。
・H264/FLV配信にメタビットレート値を載せるようにした
・履歴が重複して保存される場合があったのを修正

v1.1.0 Release 2017/04/30
・設定タブを付けた
・H264/FLVで配信出来るようにした
・オーディオをMP3,AAC,Opusから選べるようにした
・エンコーダの処理の重さを選べるようにした。画質と重さはトレードオフです

v1.0.0 Release 2017/04/26
初期バージョン

