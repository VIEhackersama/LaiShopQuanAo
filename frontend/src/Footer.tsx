import { Link } from 'react-router-dom';
import { ArrowUpRight, Sparkle } from '@phosphor-icons/react';

export function Footer() {
  return <footer className="site-footer" data-testid="site-footer">
    <div className="footer-content">
      <div className="footer-intro">
        <Link className="brand" to="/">LaiShopQuanAo</Link>
        <h2>Dễ mặc.<br /><em>Dễ là chính mình.</em></h2>
        <p>Những món đồ giản dị, dành cho nhịp sống và phong cách riêng của bạn.</p>
      </div>
      <nav className="footer-links" aria-label="Khám phá bộ sưu tập">
        <h3>Khám phá</h3>
        <Link to="/?categoryId=1">Áo <ArrowUpRight size={15} /></Link>
        <Link to="/?categoryId=2">Quần <ArrowUpRight size={15} /></Link>
        <Link to="/?categoryId=3">Váy <ArrowUpRight size={15} /></Link>
        <Link to="/">Tất cả sản phẩm <ArrowUpRight size={15} /></Link>
      </nav>
      <div className="footer-assistant">
        <span className="footer-ai-icon"><Sparkle size={24} weight="light" /></span>
        <h3>Chưa biết chọn gì?</h3>
        <p>Kể về món đồ bạn đang tìm.<br />Để trợ lý AI tìm cùng bạn.</p>
        <Link to="/assistant" data-testid="footer-assistant-link">Trò chuyện với trợ lý <ArrowUpRight size={19} /></Link>
      </div>
    </div>
    <div className="footer-bottom"><small>© {new Date().getFullYear()} LaiShopQuanAo</small><span>Mặc thoải mái. Sống theo cách của bạn.</span><small>COD · Chuyển khoản</small></div>
  </footer>;
}
