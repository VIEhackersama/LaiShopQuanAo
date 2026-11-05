import { Link } from 'react-router-dom';
import { ArrowLeft } from '@phosphor-icons/react';
import { AiSearchPanel } from './AiSearchPanel';

export function AssistantPage() {
  return <main className="assistant-page" data-testid="assistant-page">
    <div className="assistant-page-heading"><div><p className="eyebrow">LAISHOPQUANAO / YOUR PERSONAL FINDER</p><h1>Trợ lý tìm kiếm AI</h1></div><Link className="back-link" to="/"><ArrowLeft size={16} />Về bộ sưu tập</Link></div>
    <AiSearchPanel />
  </main>;
}
