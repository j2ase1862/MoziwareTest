# -*- coding: utf-8 -*-
"""뉴럴 한국어 내레이션 생성 — edge-tts(ko-KR-SunHiNeural).

manifest.json 의 슬라이드별 내레이션을 work/narr-NN.mp3 로 합성한다.
Avast 등 기업 TLS 가로채기 환경에서도 동작하도록 truststore 로 OS 신뢰저장소를 사용.
키/요금 없는 Microsoft Edge 온라인 뉴럴 음성.
"""
import os
import json
import asyncio
import truststore
truststore.inject_into_ssl()   # Windows 인증서 저장소 사용(Avast MITM 루트 신뢰)
import edge_tts

OUT = r"D:\Repo\Android\poc-video"
WORK = os.path.join(OUT, "work")
# 음성/속도/피치는 환경변수로 조정(make_video.ps1 이 전달). 기본은 따뜻한 여성 뉴럴.
VOICE = os.environ.get("TTS_VOICE", "ko-KR-SunHiNeural")
RATE = os.environ.get("TTS_RATE", "-5%")     # 예: "-5%", "+3%"
PITCH = os.environ.get("TTS_PITCH", "+0Hz")  # 예: "+0Hz", "+10Hz"(밝은 톤)
os.makedirs(WORK, exist_ok=True)


async def synth(text, path):
    await edge_tts.Communicate(text, VOICE, rate=RATE, pitch=PITCH).save(path)


async def main():
    with open(os.path.join(OUT, "manifest.json"), encoding="utf-8") as f:
        manifest = json.load(f)
    for s in manifest:
        path = os.path.join(WORK, f"narr-{s['index']:02d}.mp3")
        await synth(s["narration"], path)
        print(f"slide {s['index']:02d}: {os.path.getsize(path)} bytes")
    print("done:", VOICE, "rate", RATE, "pitch", PITCH)


asyncio.run(main())
