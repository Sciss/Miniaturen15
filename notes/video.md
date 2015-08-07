Convert frame sequence to h.264 movie using libav:

    avconv -i 'lya_7f728bd6-%d.png' -vcodec libxvid -r 25 -q 100 -pass 1 -vf "scale=1080:1080" -aspect 1:1 -vb 6M -threads 0 -f mp4 out.mp4
