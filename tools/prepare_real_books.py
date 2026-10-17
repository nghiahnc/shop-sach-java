"""Import verified edition metadata and cache original covers for the portfolio demo."""
from pathlib import Path
import json,urllib.request,time,concurrent.futures,io,uuid
from PIL import Image,ImageOps
root=Path(__file__).resolve().parents[1]
refs=json.loads((root/'.local/books-reference.json').read_text(encoding='utf-8'))
isbns=['9780061122415','9780156012195','9780061120084','9781847941831','9781455586691','9780345472328','9780132350884','9780134685991','9781492078005','9780465050659','9780761169253','9780714832470','9780753555194','9780307887894','9780066620992','9780143130727','9781607747307','9780241283912']
authors=['Paulo Coelho','Antoine de Saint-Exupéry','Harper Lee','James Clear','Cal Newport','Carol S. Dweck','Robert C. Martin','Joshua Bloch','Eric Freeman & Elisabeth Robson','Don Norman','Austin Kleon','E. H. Gombrich','Peter Thiel & Blake Masters','Eric Ries','Jim Collins','Héctor García & Francesc Miralles','Marie Kondo','Meik Wiking']
titles=['The Alchemist','The Little Prince','To Kill a Mockingbird','Atomic Habits','Deep Work','Mindset','Clean Code','Effective Java','Head First Design Patterns','The Design of Everyday Things','Steal Like an Artist','The Story of Art','Zero to One','The Lean Startup','Good to Great','Ikigai','The Life-Changing Magic of Tidying Up','The Little Book of Hygge']
descriptions=['Hành trình của Santiago đi tìm kho báu và lắng nghe điều mình thực sự mong muốn. Một câu chuyện giàu biểu tượng của Paulo Coelho.','Cuộc gặp gỡ với hoàng tử bé, những hành tinh nhỏ và các câu hỏi về tình bạn, sự quan tâm và điều mắt thường không nhìn thấy.','Tiểu thuyết của Harper Lee về tuổi thơ, lương tri và công lý tại miền Nam nước Mỹ.','Cách những thay đổi nhỏ tích lũy thành kết quả lớn. James Clear trình bày hệ thống xây dựng thói quen và môi trường hỗ trợ hành vi.','Cal Newport bàn về khả năng tập trung sâu và cách tổ chức công việc giữa những gián đoạn thường ngày.','Carol S. Dweck giới thiệu tư duy phát triển và ảnh hưởng của cách nhìn nhận năng lực tới việc học tập.','Các nguyên tắc và ví dụ về cách viết mã dễ đọc, dễ kiểm thử và dễ bảo trì, do Robert C. Martin trình bày.','Các thực hành tốt khi thiết kế và sử dụng ngôn ngữ Java. Ấn bản thứ ba của Joshua Bloch.','Design pattern được giải thích qua ví dụ trực quan và các bài tập hướng đối tượng. Ấn bản thứ hai.','Don Norman khám phá cách thiết kế đồ vật dễ hiểu, phản hồi rõ ràng và phù hợp với cách con người sử dụng.','Austin Kleon chia sẻ những gợi ý ngắn giúp nuôi dưỡng sáng tạo và phát triển tiếng nói riêng.','Một hành trình qua lịch sử nghệ thuật của E. H. Gombrich, từ những hình ảnh cổ xưa tới các trào lưu hiện đại.','Peter Thiel và Blake Masters bàn về việc xây dựng doanh nghiệp tạo ra giá trị mới thay vì chỉ lặp lại điều đã có.','Eric Ries giới thiệu cách kiểm chứng ý tưởng, học từ khách hàng và phát triển sản phẩm qua các vòng thử nghiệm.','Jim Collins trình bày nghiên cứu về những đặc điểm của doanh nghiệp chuyển mình từ tốt tới xuất sắc.','Những suy ngẫm về ý nghĩa sống, thói quen và sự gắn kết cộng đồng của Héctor García và Francesc Miralles.','Marie Kondo giới thiệu cách sắp xếp không gian sống xoay quanh những đồ vật mang lại niềm vui.','Meik Wiking khám phá hygge, sự ấm cúng và những niềm vui giản dị trong đời sống Đan Mạch.']
folder=root/'src/main/resources/static/images';folder.mkdir(parents=True,exist_ok=True)
def fetch(url):
 for attempt in range(4):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'TramSachPortfolio/1.0 (educational demo)'}),timeout=45) as r:return r.read()
  except Exception:
   if attempt==3:raise
   time.sleep(2+attempt*2)
def cover(item):
 i,isbn=item;b=refs['ISBN:'+isbn];url=(f'https://images.penguinrandomhouse.com/cover/{isbn}' if i in (6,16) else b['cover']['large']);p=folder/f'book-{i:02}.webp'
 if not p.exists():
  im=Image.open(io.BytesIO(fetch(url))).convert('RGB');assert min(im.size)>100,(isbn,im.size)
  im.thumbnail((720,1080));im.save(p,'WEBP',quality=88,method=6)
 return {'id':i,'isbn':isbn,'title':titles[i-1],'author':authors[i-1],'editionUrl':b['url'],'imageSource':url,'file':f'/images/{p.name}','size':list(Image.open(p).size)}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:assets=list(pool.map(cover,enumerate(isbns,1)))
photos=[('hero-library',3747576,'Photo Of Books On Shelves','Polina Zimmerman'),('reading-room',3747555,'Books in Library','Polina Zimmerman')]
for name,photo,title,author in photos:
 p=folder/f'{name}.webp';url=f'https://images.pexels.com/photos/{photo}/pexels-photo-{photo}.jpeg?auto=compress&cs=tinysrgb&w=2000'
 if not p.exists():
  im=Image.open(io.BytesIO(fetch(url))).convert('RGB');im.thumbnail((2000,1500));im.save(p,'WEBP',quality=85,method=6)
 assets.append({'file':f'/images/{p.name}','photographer':author,'source':f'https://www.pexels.com/photo/{photo}/','license':'https://www.pexels.com/license/'})
(root/'src/main/resources/book-assets.json').write_text(json.dumps(assets,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
sql=['-- Update only the original demo editions; preserve identifiers, inventory, prices and order snapshots.']
def quote(s):return "'"+s.replace("'","''")+"'"
for i,title in enumerate(titles,1):
 desc=descriptions[i-1]+f' Ấn bản tiếng Anh, ISBN {isbns[i-1]}. Giá và hoạt động mua hàng chỉ phục vụ demo.'
 sql.append(f"UPDATE books SET title={quote(title)},author={quote(authors[i-1])},description={quote(desc)} WHERE id='{uuid.UUID(int=100+i)}';")
(root/'src/main/resources/db/migration/V4__real_book_editions.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
lines=['# Nguồn ảnh Trạm Sách','','Ảnh bìa đối chiếu theo ISBN/ấn bản trên Open Library, lưu cục bộ để trình diễn danh mục. Bản quyền bìa thuộc chủ sở hữu tương ứng; Trạm Sách là portfolio, không phải đơn vị phát hành.','','| Sách | Tác giả | ISBN | Ấn bản | Ảnh |','|---|---|---|---|---|']
for a in assets[:18]:lines.append(f"| {a['title']} | {a['author']} | {a['isbn']} | [Open Library]({a['editionUrl']}) | [Nguồn]({a['imageSource']}) |")
lines+=['','Ảnh chụp thư viện: Polina Zimmerman, [3747576](https://www.pexels.com/photo/photo-of-books-on-shelves-3747576/) và [3747555](https://www.pexels.com/photo/books-in-library-3747555/), theo [Pexels License](https://www.pexels.com/license/).','', 'Phong cách bố cục tham khảo Louis Vuitton; nhận diện, nội dung và sản phẩm của Trạm Sách được xây dựng riêng.']
(root/'docs/ASSET_SOURCES.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(f'Cached {len(assets)} verified images; V4 updates only catalog metadata.')
