#!/usr/bin/env python3
"""Check the pinned runtime's action-feedback boundary with synthetic observations."""
import asyncio
from pathlib import Path
import sys
import tempfile
import types
import zipfile

ROOT = Path(__file__).resolve().parents[2]

with tempfile.TemporaryDirectory() as temporary:
    with zipfile.ZipFile(ROOT / 'artifacts/omniflow-gui-runtime-2.1.8.zip') as archive:
        for name in archive.namelist():
            if name.startswith('scripts/runtime/python/') and name.endswith(('.py', '.json')):
                archive.extract(name, temporary)
    root = Path(temporary) / 'scripts/runtime/python/omniflow'
    # Load only the core modules under test; the package's public initializer
    # also imports device-only inference dependencies unrelated to this check.
    for name, path in [('omniflow', root), ('omniflow.core', root / 'core'), ('omniflow.runtime', root / 'runtime')]:
        module = types.ModuleType(name)
        module.__path__ = [str(path)]
        sys.modules[name] = module
    from omniflow.core.model import Action, Observation
    from omniflow.core.config import PluginSet
    import omniflow.runtime.core as core

    core._ACTION_SETTLE_SECONDS = 0

    class Host:
        def __init__(self, after):
            self.after = after

        def act(self, action):
            return {'success': True, 'extra': {'message': 'gesture_dispatched'}}

        def observe(self, **kwargs):
            return self.after

    async def check():
        before = Observation(xml='<node focused="false"/>', image_base64='before')
        cases = [
            ('unchanged click', Action('click', {'x': 1, 'y': 1}), before),
            ('focus click', Action('click', {'x': 1, 'y': 1}), Observation(xml='<node focused="true"/>', image_base64='before')),
            ('drawing swipe', Action('swipe', {'direction': 'right'}), Observation(xml=before.xml, image_base64='stroke')),
            ('date swipe', Action('swipe', {'direction': 'up'}), Observation(xml='<node text="Nov 6"/>', image_base64='date')),
        ]
        for label, action, after in cases:
            result = await core.execute_action(action, observation=before, host=Host(after), plugins=PluginSet())
            assert result.success and result.error is None and result.after == after
            assert result.result.extra == {'message': 'gesture_dispatched'}
            print(f'{label}: dispatched success, no effect-verification signal')

    asyncio.run(check())
