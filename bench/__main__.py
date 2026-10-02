import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description='Offline Sakshi benchmark; downloads require explicit prepare commands')
    parser.add_argument('component', choices=['data', 'validate-data', 'prepare-audio', 'prepare-whisper', 'registry', 'classifier', 'language', 'ocr', 'stt', 'extraction', 'linking', 'storage', 'integrity', 'pdf', 'ingest', 'stacks', 'report'])
    parser.add_argument('--candidate', default=None)
    parser.add_argument('--limit', type=int, default=0, help='0 = full applicable dataset; subset results are marked')
    parser.add_argument('--vad', action='store_true')
    parser.add_argument('--ram-cap-gb', type=float, default=None, help='Laptop process admission/observed RSS guard, NOT Android simulation')
    args = parser.parse_args()
    if args.limit < 0 or (args.ram_cap_gb is not None and args.ram_cap_gb <= 0):
        parser.error('limit must be nonnegative and RAM guard must be positive')
    if args.component == 'data':
        from .data import build
        build()
    elif args.component == 'validate-data':
        from .data import validate
        print(validate(Path('data')))
    elif args.component == 'prepare-audio':
        from .prepare import public_audio
        public_audio()
    elif args.component == 'prepare-whisper':
        from .prepare import whisper_model
        whisper_model()
    elif args.component == 'registry':
        from .registry import write_registry
        write_registry()
    elif args.component == 'report':
        from .report import report
        report()
    elif args.component == 'stacks':
        from .pipeline import run_stacks
        run_stacks(args)
    else:
        from .runner import run
        run(args)


if __name__ == '__main__':
    main()
