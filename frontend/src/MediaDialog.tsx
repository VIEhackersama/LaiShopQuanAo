import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Camera, Microphone, X, Stop } from '@phosphor-icons/react';
import { api, capabilitiesSchema, searchSchema } from './api';
import { ErrorMessage } from './shared';

export function MediaDialog({ mode, onClose }: { mode: 'voice' | 'image'; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recognitionRef = useRef<any>(null);
  const speechTextRef = useRef<string>('');

  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [recording, setRecording] = useState(false);
  const [durationSeconds, setDurationSeconds] = useState<number | null>(null);
  const [localError, setLocalError] = useState<Error | null>(null);
  const [transcript, setTranscript] = useState('');
  const [voiceId, setVoiceId] = useState<number | null>(null);
  const [crop, setCrop] = useState(false);
  const [region, setRegion] = useState({ x: 0, y: 0, width: 1, height: 1 });

  const capabilities = useQuery({ queryKey: ['capabilities'], queryFn: () => api('/search/capabilities', capabilitiesSchema, {}) });
  const enabled = capabilities.data?.[mode] === true;

  useEffect(() => {
    dialog.current?.showModal();
    return () => {
      stream.current?.getTracks().forEach(track => track.stop());
      if (timer.current) clearTimeout(timer.current);
      try { recognitionRef.current?.stop(); } catch { /* ignore */ }
    };
  }, []);

  useEffect(() => {
    if (!file) { setPreview(''); return; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const upload = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Chọn file trước khi tìm kiếm.');
      if (file.size > 10 * 1024 * 1024) throw new Error('File tối đa 10 MB.');
      if (crop && (region.x + region.width > 1 || region.y + region.height > 1)) throw new Error('Vùng chọn phải nằm trong ảnh.');
      const form = new FormData();
      form.append('file', file);
      if (mode === 'voice') {
        if (durationSeconds === null) throw new Error('Không xác định được thời lượng bản ghi.');
        form.append('durationSeconds', durationSeconds.toFixed(3));
      }
      if (mode === 'image') {
        form.append('options', new Blob([JSON.stringify({
          conditions: { categoryId: null, sizes: [], colors: [], minPrice: null, maxPrice: null, sort: 'RELEVANCE' },
          region: crop ? region : null
        })], { type: 'application/json' }));
      }
      return api(`/search/${mode}`, searchSchema, { method: 'POST', body: form });
    },
    onSuccess: result => {
      if (mode === 'voice') {
        setVoiceId(result.queryId);
        const finalTranscript = speechTextRef.current.trim() || result.text || '';
        setTranscript(finalTranscript);
      } else {
        navigate(`/search?queryId=${result.queryId}`);
        onClose();
      }
    }
  });

  const execute = useMutation({
    mutationFn: () => api(`/search/voice/${voiceId}/execute`, searchSchema, {
      method: 'POST',
      body: JSON.stringify({
        text: transcript,
        conditions: { categoryId: null, sizes: [], colors: [], minPrice: null, maxPrice: null, sort: 'RELEVANCE' },
        page: 0,
        pageSize: 24
      })
    }),
    onSuccess: result => {
      navigate(`/search?queryId=${result.queryId}`);
      onClose();
    }
  });

  async function start() {
    setLocalError(null);
    speechTextRef.current = '';
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setLocalError(new Error('Trình duyệt chưa hỗ trợ ghi âm. Hãy dùng trình duyệt hiện đại qua HTTPS.'));
      return;
    }
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ audio: true });
      const type = ['audio/webm', 'audio/mp4'].find(value => MediaRecorder.isTypeSupported(value));
      if (!type) throw new Error('Trình duyệt không hỗ trợ định dạng ghi âm WebM hoặc MP4.');

      // Start browser native SpeechRecognition in parallel if available
      const SpeechRec = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (SpeechRec) {
        try {
          const rec = new SpeechRec();
          rec.lang = 'vi-VN';
          rec.continuous = true;
          rec.interimResults = true;
          rec.onresult = (event: any) => {
            let captured = '';
            for (let i = 0; i < event.results.length; i++) {
              captured += event.results[i][0].transcript;
            }
            if (captured) {
              speechTextRef.current = captured;
            }
          };
          rec.start();
          recognitionRef.current = rec;
        } catch { /* ignore */ }
      }

      const current = new MediaRecorder(stream.current, { mimeType: type });
      recorder.current = current;
      const chunks: Blob[] = [];
      const started = Date.now();
      current.ondataavailable = event => chunks.push(event.data);
      current.onstop = () => {
        const elapsed = Math.min(30, (Date.now() - started) / 1000);
        setRecording(false);
        try { recognitionRef.current?.stop(); } catch { /* ignore */ }
        stream.current?.getTracks().forEach(track => track.stop());
        if (timer.current) clearTimeout(timer.current);
        if (elapsed < 1) {
          setLocalError(new Error('Bản ghi cần dài ít nhất 1 giây.'));
          return;
        }
        setDurationSeconds(elapsed);
        setFile(new File(chunks, type === 'audio/webm' ? 'voice.webm' : 'voice.mp4', { type }));
      };
      current.start();
      setRecording(true);
      timer.current = setTimeout(() => current.state === 'recording' && current.stop(), 30000);
    } catch (error) {
      stream.current?.getTracks().forEach(track => track.stop());
      setLocalError(error instanceof Error ? error : new Error('Không thể truy cập microphone.'));
    }
  }

  return (
    <dialog ref={dialog} className="media-dialog" onCancel={onClose} aria-labelledby="media-title">
      <button aria-label="Đóng" className="icon-button dialog-close" onClick={onClose}><X size={24} /></button>
      {mode === 'voice' ? <Microphone size={36} weight="light" /> : <Camera size={36} weight="light" />}
      <p className="eyebrow">MỘT CÁCH KHÁC ĐỂ TÌM KIẾM</p>
      <h2 id="media-title">{mode === 'voice' ? 'Nói điều bạn đang tìm.' : 'Tìm từ một hình ảnh.'}</h2>
      <p>{mode === 'voice' ? 'Ghi âm 1–30 giây, xem và chỉnh phiên âm trước khi tìm.' : 'JPEG hoặc PNG · Tối đa 10 MB · Có thể chọn vùng ảnh.'}</p>
      {!enabled && capabilities.data && (
        <div className="notice" data-testid="provider-unavailable">
          Chức năng đang chờ tích hợp dịch vụ xử lý từ thành viên phụ trách. Tìm kiếm bằng văn bản vẫn sẵn sàng.
        </div>
      )}
      <ErrorMessage error={capabilities.error} />
      <ErrorMessage error={localError} />
      <ErrorMessage error={upload.error} />
      <ErrorMessage error={execute.error} />

      {mode === 'voice' ? (
        <>
          <button data-testid="record-voice" disabled={!enabled || upload.isPending} onClick={() => recording ? recorder.current?.stop() : void start()}>
            {recording ? <><Stop /> Dừng ghi âm</> : <><Microphone /> Bắt đầu ghi âm</>}
          </button>
          {preview && <audio controls src={preview} />}
          {voiceId !== null && (
            <label>
              Phiên âm
              <input data-testid="voice-transcript" maxLength={100} value={transcript} onChange={e => setTranscript(e.target.value)} />
              <button disabled={execute.isPending || !transcript.trim()} onClick={() => execute.mutate()}>
                Tìm với nội dung này
              </button>
            </label>
          )}
        </>
      ) : (
        <>
          <input
            data-testid="image-file"
            type="file"
            accept="image/jpeg,image/png"
            disabled={!enabled}
            onChange={e => {
              const next = e.target.files?.[0];
              if (next && !['image/jpeg', 'image/png'].includes(next.type)) {
                setLocalError(new Error('Chỉ hỗ trợ JPEG hoặc PNG.'));
                return;
              }
              setFile(next ?? null);
            }}
          />
          {preview && <img className="upload-preview" src={preview} alt="Ảnh đã chọn" />}
          <label className="check-label">
            <input type="checkbox" disabled={!enabled} checked={crop} onChange={e => setCrop(e.target.checked)} />
            Chọn vùng ảnh
          </label>
          {crop && (
            <div className="crop-inputs">
              {(['x', 'y', 'width', 'height'] as const).map(key => (
                <label key={key}>
                  {key}
                  <input
                    type="number"
                    step="0.01"
                    min={key === 'width' || key === 'height' ? '0.01' : '0'}
                    max="1"
                    value={region[key]}
                    onChange={e => setRegion({ ...region, [key]: Number(e.target.value) })}
                  />
                </label>
              ))}
            </div>
          )}
        </>
      )}

      <button
        className="full"
        data-testid="media-submit"
        disabled={!enabled || !file || recording || upload.isPending || voiceId !== null}
        onClick={() => upload.mutate()}
      >
        {upload.isPending ? 'Đang xử lý…' : mode === 'voice' ? 'Nhận dạng bản ghi' : 'Tìm sản phẩm tương tự'}
      </button>
    </dialog>
  );
}
