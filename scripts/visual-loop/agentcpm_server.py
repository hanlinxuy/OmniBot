#!/usr/bin/env python3
"""Loopback-only adapter for audited AgentCPM-GUI weights, not an OmniBot provider."""
import argparse
import base64
import io
import json
import time

from aiohttp import web
from PIL import Image
import jsonschema
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', required=True)
    p.add_argument('--schema', required=True, help='Pinned upstream eval/utils/schema/schema.json')
    p.add_argument('--port', type=int, default=8090)
    args = p.parse_args()
    schema = json.load(open(args.schema))
    prompt = ('# Role\n你是一名熟悉安卓系统触屏GUI操作的智能体，将根据用户的问题，分析当前界面的GUI元素和布局，生成相应的操作。'
              '\n\n# Task\n针对用户问题，根据输入的当前屏幕截图，输出下一步的操作。'
              '\n\n# Rule\n- 以紧凑JSON格式输出\n- 输出操作必须遵循Schema约束'
              '\n\n# Schema\n' + json.dumps(schema, ensure_ascii=False, separators=(',', ':')))
    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True, local_files_only=True)
    model = AutoModelForCausalLM.from_pretrained(args.model, trust_remote_code=True,
        local_files_only=True, torch_dtype=torch.bfloat16, attn_implementation='sdpa').eval().cuda()
    validator = jsonschema.Draft202012Validator(schema)

    async def health(request):
        return web.json_response({'model': 'AgentCPM-GUI', 'dtype': 'bfloat16', 'images': True})

    async def act(request):
        body = await request.json()
        img = Image.open(io.BytesIO(base64.b64decode(body['images'][0].split(',', 1)[1], validate=True))).convert('RGB')
        img.thumbnail((1120, 1120), Image.Resampling.LANCZOS)
        state = body['state']
        goal = state['goal'] + '\nRecent executed actions: ' + json.dumps(state['recent_actions'])
        messages = [{'role': 'user', 'content': [f'<Question>{goal}</Question>\n当前屏幕截图：', img]}]
        started = time.monotonic()
        with torch.inference_mode():
            raw = model.chat(image=None, msgs=messages, system_prompt=prompt, tokenizer=tokenizer,
                sampling=False, num_beams=1, max_new_tokens=192)
        decoded = json.loads(raw)
        validator.validate(decoded)
        operations = [k for k in ('POINT', 'PRESS', 'TYPE') if k in decoded]
        if len(operations) > 1 or 'to' in decoded:
            raise web.HTTPUnprocessableEntity(text='Composite and swipe actions are not yet supported by this bounded adapter')
        x = y = 0
        if 'POINT' in decoded:
            action = 'tap'
            x, y = decoded['POINT']
        elif 'TYPE' in decoded:
            literals = body.get('text_literals', [])
            if decoded['TYPE'] not in literals:
                raise web.HTTPUnprocessableEntity(text='Text is outside the user-supplied literals')
            action = f"text_{literals.index(decoded['TYPE'])}"
        elif decoded.get('PRESS') == 'BACK':
            action = 'back'
        elif decoded.get('PRESS'):
            raise web.HTTPUnprocessableEntity(text='Unsupported special key')
        elif decoded.get('STATUS') in ('finish', 'satisfied'):
            action = 'done'
        else:
            raise web.HTTPUnprocessableEntity(text='No executable action or successful completion in response')
        return web.json_response({'answers': {'action': {'choice': action}, 'x': {'choice': str(x)},
            'y': {'choice': str(y)}}, 'native_action': decoded, 'inference_seconds': time.monotonic() - started})

    app = web.Application(client_max_size=8 * 1024 * 1024)
    app.router.add_get('/health', health)
    app.router.add_post('/act', act)
    web.run_app(app, host='127.0.0.1', port=args.port)


if __name__ == '__main__':
    main()
